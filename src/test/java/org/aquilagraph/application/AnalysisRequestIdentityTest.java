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
        "analysis-request:aad4fa7d40c4a8303828e7fdfee69309bdfc6b632d1f4c91f82278d32a1ffba9",
        "analysis-request:3379b58b0f6ec11ded7de01a86bd4fc59aa774c9ce6cc1a9b4332fad5502b49c");

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
        "analysis-request:aad4fa7d40c4a8303828e7fdfee69309bdfc6b632d1f4c91f82278d32a1ffba9",
        "analysis-request:85db8eab3cae2b3f789f69e68d2613eafbe37ac78331144883d11c362bac34ce",
        "analysis-request:f70d8b1d1976ff9be154ffff31c910df92ba8f8aba94c5a9e372b87eade74484",
        "analysis-request:3db043ab3797866a953fc2109082bf212dd89d2a248325fff1e7f5dfa225af5d",
        "analysis-request:cb95937205e42dc9323680e40369ed1072ff379ed51513a9321cebf20cace6a8",
        "analysis-request:46fefd39ba887992ca16c721624da3343d69caa5baa70088722415b72aab1a4a");
    var expectedExisting = List.of(
        "analysis-request:3379b58b0f6ec11ded7de01a86bd4fc59aa774c9ce6cc1a9b4332fad5502b49c",
        "analysis-request:a450183cad97fd94e0f04cd958283189a39c2e884959bf473fa22876640b57d8",
        "analysis-request:e69630c244b0c4312bda11c032a363c1d2ea44a6b4bb260cc3636b2e8307272a",
        "analysis-request:45afe2ea36c2ffcd2b10c9747e7d9754b6c58b613998af3325955b4b38487633",
        "analysis-request:9042d7f3ffbfd9331e77d43d0052cc8a007cabcbd0a78f7827955421db958cb7",
        "analysis-request:cc1925bbec92650dac7de12b55b7defacde0890bd3cd215807a2d0ede2fc67c9");
    var expectedUnavailable = List.of(
        "analysis-request:c34d9b5a26516ba595bbf1c8d3220bcb6f159e63630914644997c86ae622baa5",
        "analysis-request:dd51e5703ea122ba3674621d3ce0d1a2dc5562275f8505818023d1f7885db880",
        "analysis-request:6915f2e83b91ca8e4f1bbe34ad6e721fa5e8d60ba379aae15f698f025aff9f9a",
        "analysis-request:1148051db1d5c863e20eb9d9aa3ce37f4c295e604806e72418e2062dcf2ecb40",
        "analysis-request:8fc604cf0f8f9c962e32df6732aade85ca47383e914a368da0310ed62ffd997a",
        "analysis-request:334847ceb008a61e2a5b0c90e5c5c1e66915c59b13723467ff73b71e4bdbadf7");

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
        "analysis-request:57ef7bfaf4e9c8743a4ad840d4aae61e87501b0f954442dcad1529b81a7c3e72",
        "analysis-request:adfa8854026c4caeff8c6d9294b69bc481e54e9819b8d7047709122968718d8d",
        "analysis-request:103a4bf4a52bedd3b6bcbcfdf25014f4370faa0a2c6c3003ffe03299396104ed",
        "analysis-request:151d5ee28da12e44d5c0025dc82fe746111810fdfa0ad8b6e53c5305006e16c2",
        "analysis-request:0b3351e77084a18bc45154d9c24b709b577dff00d7c323726f8a3044cb810c9c");

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
    assertEquals("analysis-request:07ef0cb1475efe0ec427e97cbbd4175a9c46cb05d363116118ddd60aaa561198", profile);
    assertEquals("analysis-request:888bb1f7cb512f8f02a453122684a672c22b3aa92323812c38b43bbb79ae5a52", capture);
  }

  private static AnalysisRequestIdentity production(AstIdentity ast, CfgSelection selection) {
    return new AnalysisRequestIdentity(AnalysisOperation.PRODUCTION_WITH_CFG, new AvailableSource(ast),
        new ProductionSelection(Optional.of(ast), selection));
  }

  private static AstIdentity identity(String software, String format, String profile, String revision, String content) {
    return AstIdentity.fromContent(software, format, profile, revision, content);
  }
}
