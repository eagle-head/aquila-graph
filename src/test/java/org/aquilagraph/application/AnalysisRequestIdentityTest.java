package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.aquilagraph.application.AnalysisRequestIdentity.AvailableSource;
import org.aquilagraph.application.AnalysisRequestIdentity.ExistingSelection;
import org.aquilagraph.application.AnalysisRequestIdentity.NotRequested;
import org.aquilagraph.application.AnalysisRequestIdentity.ProductionSelection;
import org.aquilagraph.application.AnalysisRequestIdentity.UnavailableSource;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.identity.api.AstIdentity;
import org.junit.jupiter.api.Test;

class AnalysisRequestIdentityTest {
  @Test
  void givenAllBodyAndExplicitSelections_whenIdentifyingRequests_thenScopeAndResolvedOrderRemainPartOfProvenance() {
    // Arrange
    var ast = identity("software", "source/1", "profile/1", "production/1", "content-a");
    var keys = List.of("first", "second");
    var all = CfgSelection.allBodies(CfgProfile.JAVA_BOUNDED);
    var explicit = production(ast, new CfgSelection(keys, CfgProfile.JAVA_BOUNDED));

    // Act
    var bound = new AnalysisRequestIdentity(AnalysisOperation.PRODUCTION_WITH_CFG, new AvailableSource(ast),
        new ProductionSelection(Optional.of(ast), all, keys));
    var reversed = new AnalysisRequestIdentity(AnalysisOperation.PRODUCTION_WITH_CFG, new AvailableSource(ast),
        new ProductionSelection(Optional.of(ast), all, List.of("second", "first")));

    // Assert
    assertNotEquals(explicit.key(), bound.key());
    assertNotEquals(bound.key(), reversed.key());
    assertNotEquals(production(ast, new CfgSelection(List.of(), CfgProfile.JAVA_BOUNDED)).key(), production(ast, all).key());
    assertThrows(IllegalArgumentException.class, () -> new ProductionSelection(Optional.empty(), all, keys));
    assertThrows(IllegalArgumentException.class, () -> new CfgSelection(keys, CfgProfile.JAVA_BOUNDED, CfgSelection.Scope.ALL_BODIES));
    assertThrows(IllegalArgumentException.class,
        () -> new ProductionSelection(Optional.of(ast), new CfgSelection(keys, CfgProfile.JAVA_BOUNDED), List.of("first")));
  }

  @Test
  void givenAbsentAndEmptySelections_whenIdentifyingEachOperation_thenIndependentFixedKeysDistinguishTheirProvenance() {
    // Arrange
    var ast = identity("software", "source/1", "profile/1", "production/1", "content-a");
    var subject = new AvailableSource(ast);
    var empty = new CfgSelection(List.of(), CfgProfile.JAVA_BOUNDED);
    var expected = List.of(
        "analysis-request:4af00b98bcb8647ee79f0f0f9f3a538276949c07812a7c0e5b327d0ae3c67869",
        "analysis-request:2aec11303ce1a9407e8de7bf835839ddd98ed720c68c20b9cbab7de1c7e4c990",
        "analysis-request:954c60c9da72e34e278f90d3cba4486a6484ade7bd91d079a06453839ff3e409");

    // Act
    var requests = List.of(new AnalysisRequestIdentity(AnalysisOperation.AST_ONLY, subject, NotRequested.INSTANCE),
        new AnalysisRequestIdentity(AnalysisOperation.PRODUCTION_WITH_CFG, subject, new ProductionSelection(Optional.of(ast), empty)),
        new AnalysisRequestIdentity(AnalysisOperation.DERIVE_EXISTING, subject,
            new ExistingSelection(new CfgRequest(ast, List.of(), CfgProfile.JAVA_BOUNDED))));
    var keys = requests.stream().map(AnalysisRequestIdentity::key).toList();

    // Assert
    assertEquals(expected, keys);
    assertThrows(IllegalArgumentException.class, () -> new AnalysisRequestIdentity(AnalysisOperation.PRODUCTION_WITH_CFG, subject,
        new ProductionSelection(Optional.empty(), empty)));
    assertThrows(IllegalArgumentException.class, () -> new AnalysisRequestIdentity(AnalysisOperation.AST_ONLY, subject,
        new ExistingSelection(new CfgRequest(ast, List.of(), CfgProfile.JAVA_BOUNDED))));
  }

  @Test
  void givenOrderedInvalidAndUnavailableSelections_whenIdentifyingRequests_thenFixedKeysRetainEveryRequestedPositionAndBindingVariant() {
    // Arrange
    var ast = identity("software", "source/1", "profile/1", "production/1", "content-a");
    var selections = List.of(List.<String>of(), List.of("first", "second"), List.of("second", "first"), List.of("first", "first"),
        List.of(""), List.of("unknown"));
    var expectedProduction = List.of(
        "analysis-request:2aec11303ce1a9407e8de7bf835839ddd98ed720c68c20b9cbab7de1c7e4c990",
        "analysis-request:3c7a7dfd6ac35f068a7004b5f19da75344617c955e1efcfd32b5a7f1657d4c9d",
        "analysis-request:92fbbc07d2dfdadea5a038d978c86d87bb590ec73f442274a76a9894e0775b3d",
        "analysis-request:551e6182b6caaafb4008e85768bbf3c6751db1ece5099baa4b09f3f496097a51",
        "analysis-request:358f48d544484b5beb00c428ae316fc7f3bdbeaeb73b2f0da5b639ab3fbee279",
        "analysis-request:b8a1028c89a1ca13e77f2d009020f9924def055f3d34d7e9cf82dd39c149c3bc");
    var expectedExisting = List.of(
        "analysis-request:954c60c9da72e34e278f90d3cba4486a6484ade7bd91d079a06453839ff3e409",
        "analysis-request:05c876e65ccca39d952548075b094a09654173bef4f432829220994f8ffc0365",
        "analysis-request:ec964a8f8663be4290b4c7c3d548c91118217fc8f037393bd53727035e571901",
        "analysis-request:8190342e2bc196b2b93a480495e98bb589e1635d21db27fae0a2e0add4646244",
        "analysis-request:921a03af9f9ee90316ff2a31b67e4fbe42acbc23955202357d519552c974477a",
        "analysis-request:2b1ae1d0321fbf70e3076cf4cb6b58b812fde90bbce7625d1af9d03e6f7cc221");
    var expectedUnavailable = List.of(
        "analysis-request:964c711442aa59b4831679b2771a87e4f1fd426d5f252f620786e05ae0b67f69",
        "analysis-request:6aca6bde3d0f97627a6b6cee79375431fd42c04f0dd08987dfc3a77fbed6da78",
        "analysis-request:c199471ab1b08316498b7cc08b5028a4b21a07df22c1924b0d47885f7dee1501",
        "analysis-request:3a4e4ad7c6ffc8d596442a7548cc75bbc6f9e4420c2996709c9da76b78aa2cc8",
        "analysis-request:2126258db254a1c022af5869c4c0e430b070edee7f49255a5cbe5221f5ec5e52",
        "analysis-request:fd331d27f6ed63a4b1b32becaf592ed1bc5e5426d3b57344250fd1b6b68a9ce8");

    // Act
    var produced = selections.stream().map(selection -> production(ast, new CfgSelection(selection, CfgProfile.JAVA_BOUNDED)).key()).toList();
    var existing = selections.stream().map(selection -> new AnalysisRequestIdentity(AnalysisOperation.DERIVE_EXISTING,
        new AvailableSource(ast), new ExistingSelection(new CfgRequest(ast, selection, CfgProfile.JAVA_BOUNDED))).key()).toList();
    var unavailable = selections.stream().map(selection -> new AnalysisRequestIdentity(AnalysisOperation.PRODUCTION_WITH_CFG,
        new UnavailableSource("profile/1", "captured-digest"),
        new ProductionSelection(Optional.empty(), new CfgSelection(selection, CfgProfile.JAVA_BOUNDED))).key()).toList();

    // Assert
    assertEquals(expectedProduction, produced);
    assertEquals(expectedExisting, existing);
    assertEquals(expectedUnavailable, unavailable);
  }

  @Test
  void givenEachSourceAndGraphQualificationComponent_whenIdentifyingProducedRequests_thenIndependentFixedKeysBindEveryComponent() {
    // Arrange
    var ast = identity("software", "source/1", "profile/1", "production/1", "content-a");
    var selection = List.of("first", "second");
    var sources = List.of(identity("software-changed", "source/1", "profile/1", "production/1", "content-a"),
        identity("software", "source/1-changed", "profile/1", "production/1", "content-a"),
        identity("software", "source/1", "profile/1-changed", "production/1", "content-a"),
        identity("software", "source/1", "profile/1", "production/1-changed", "content-a"),
        identity("software", "source/1", "profile/1", "production/1", "content-a-changed"));
    var expected = List.of(
        "analysis-request:7d9c5ec9bdaa58e6f3957779b3aa3f416ad5351428d7ce77a8706387e944b1fc",
        "analysis-request:604152db9a4fe3e4943936910b761a0b5f9fc10cf237fda3acc566e1a769f69e",
        "analysis-request:399442dfc3d06bf9ac6769dba8838a770c9295d02a2425ca1a103605b96f8f48",
        "analysis-request:0d2791eeeb06a7336f2a26ee2be53ef876356b540c4f5568b226d11f2f92c27f",
        "analysis-request:21de5d32e261c4bde0a6890b7e261adb25b1891b6a71210d5810f0bd607d6bdb");

    // Act
    var keys = sources.stream().map(source -> production(source, new CfgSelection(selection, CfgProfile.JAVA_BOUNDED)).key()).toList();
    var changedProfile = production(ast, new CfgSelection(selection, new CfgProfile("cfg/changed", "java-cfg/1"))).key();
    var changedFormat = production(ast, new CfgSelection(selection, new CfgProfile("java-cfg-bounded/1", "graph/changed"))).key();

    // Assert
    assertEquals(expected, keys);
    assertEquals("analysis-request:07f4f9141443eba2feb659c68304b022b0e9605515b44306a928e6d6b32e9078", changedProfile);
    assertEquals("analysis-request:52e4cd1aac93fd80b9a7f091353e90db06ba090bb11848302b53cd58857c73e3", changedFormat);
  }

  @Test
  void givenUnavailableProfileOrCaptureChanges_whenIdentifyingRequests_thenIndependentKeysPreserveBothSubjectComponents() {
    // Arrange
    var selection = new ProductionSelection(Optional.empty(), new CfgSelection(List.of(), CfgProfile.JAVA_BOUNDED));

    // Act
    var profile = new AnalysisRequestIdentity(AnalysisOperation.PRODUCTION_WITH_CFG,
        new UnavailableSource("profile/changed", "captured-digest"), selection).key();
    var capture = new AnalysisRequestIdentity(AnalysisOperation.PRODUCTION_WITH_CFG,
        new UnavailableSource("profile/1", "captured-changed"), selection).key();

    // Assert
    assertEquals("analysis-request:2e2115b2db255acc6059c9ab5ddea5f3647d46405e9419fc7fcb455687cc88f2", profile);
    assertEquals("analysis-request:ce25ee954aa296d42b285c8b886ba437ed561b620a5505b1c48687bbed3245f9", capture);
  }

  private static AnalysisRequestIdentity production(AstIdentity ast, CfgSelection selection) {
    return new AnalysisRequestIdentity(AnalysisOperation.PRODUCTION_WITH_CFG, new AvailableSource(ast),
        new ProductionSelection(Optional.of(ast), selection));
  }

  private static AstIdentity identity(String software, String format, String profile, String revision, String content) {
    return AstIdentity.fromContent(software, format, profile, revision, content);
  }
}
