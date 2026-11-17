package org.aquilagraph.cfg.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.aquilagraph.identity.api.AstIdentity;
import org.junit.jupiter.api.Test;

class CfgRequestIdentityTest {
  @Test
  void givenEmptyOrderedRepeatedBlankAndUnknownSelections_whenIdentifyingRequests_thenFixedKeysRetainEveryPosition() {
    // Arrange
    var ast = identity("software", "source/1", "profile/1", "production/1", "content-a");
    var selections = List.of(List.<String>of(), List.of("first", "second"), List.of("second", "first"), List.of("first", "first"),
        List.of(""), List.of("unknown"));
    var expected = List.of(
        "cfg-request:30f6d7eeac2a5ff6b92689e11164941b0e7ae6aaf4241c6dee6d0a65de564067",
        "cfg-request:7298aacfa80cbedda15cb043679e6bff79c0f7731afda80693cc53376c792c51",
        "cfg-request:e653839b897f71539de7c0aa6a846de923e24fee0d3a179817745f5b93d03c92",
        "cfg-request:c79edc4117b2d845243ce8e9869fb4d698124122a91c15e1a54a6d11f39c2d3f",
        "cfg-request:09d2e6f36532867a0e2f35511fe95dd0bf13d0c427e947d8a32c9a62f07b17fe",
        "cfg-request:8ad96aa5f4a251b0bb717bdd50b70b0d09b8d34584a48c4944385232c1e9602d");

    // Act
    var requests = selections.stream().map(selection -> new CfgRequest(ast, selection, CfgProfile.JAVA_BOUNDED)).toList();
    var keys = requests.stream().map(CfgRequest::identityKey).toList();

    // Assert
    assertEquals(expected, keys);
    assertEquals(selections, requests.stream().map(CfgRequest::methodKeys).toList());
  }

  @Test
  void givenEachAstAndCfgQualificationComponent_whenIdentifyingRequests_thenIndependentKeysBindTheFullTuples() {
    // Arrange
    var ast = identity("software", "source/1", "profile/1", "production/1", "content-a");
    var selection = List.of("first", "second");
    var sources = List.of(identity("software-changed", "source/1", "profile/1", "production/1", "content-a"),
        identity("software", "source/1-changed", "profile/1", "production/1", "content-a"),
        identity("software", "source/1", "profile/1-changed", "production/1", "content-a"),
        identity("software", "source/1", "profile/1", "production/1-changed", "content-a"),
        identity("software", "source/1", "profile/1", "production/1", "content-a-changed"));
    var expected = List.of(
        "cfg-request:07d78e514678ebef2d3f7c3f71d54a59f2ce20e8f88d730b44c71f0cf2b6f6a9",
        "cfg-request:7455b5cd00bd6a42618aeb01ac3a3f4dc0d3a8d7a5a924d2733657f0e0ea499b",
        "cfg-request:0b2ca98fe82ac029226c189c8aefd229a2d77e96df3affaabb8edd3ec8df9caf",
        "cfg-request:dab1db3bc3977812227ccc33b4dd9ce64596f76673f4091107a9163abb2d2dd5",
        "cfg-request:56bd75159e18e71e78f43903d27984ad9ce48e5d06b1c1189ea34c55b4410123");

    // Act
    var keys = sources.stream().map(source -> new CfgRequest(source, selection, CfgProfile.JAVA_BOUNDED).identityKey()).toList();
    var changedProfile = new CfgRequest(ast, selection, new CfgProfile("cfg/changed", "java-cfg/1")).identityKey();
    var changedFormat = new CfgRequest(ast, selection, new CfgProfile("java-cfg-bounded/1", "graph/changed")).identityKey();

    // Assert
    assertEquals(expected, keys);
    assertEquals("cfg-request:ce958762dc8420fc893fee68c7ea362ce5a6fb8533f745c7ae802ee492cce3d4", changedProfile);
    assertEquals("cfg-request:91baf444d1647ef83d2db28c069c0db1987e7160da4851f92fd3004078cab82f", changedFormat);
  }

  private static AstIdentity identity(String software, String format, String profile, String revision, String content) {
    return AstIdentity.fromContent(software, format, profile, revision, content);
  }
}
