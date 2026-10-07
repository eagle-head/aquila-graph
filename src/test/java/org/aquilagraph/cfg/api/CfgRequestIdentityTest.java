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
        "cfg-request:be6c8887426300c38de85f28db2d1b48316525239ff80765990bc9968d4f333c",
        "cfg-request:8bb8f33f604640ca51277a9dd591425b470a3ba5da0dc9662c8902f1eec60c0b",
        "cfg-request:929bf236b79dc78f170ff50b9e0a9e081d35df5e2e63607d9c4a98cbfd505381",
        "cfg-request:89478c801a62f1fadcac51772f985f3df74bf09fcbed51dc6d1ac550b65a6e81",
        "cfg-request:77545abd4b6ea64c90c0e82af06f053de11f955a363b9b1b0488b7c533565a83",
        "cfg-request:444d18a227fc20940e2618c434f1c80fcf0c4fed88b3aac566ae43cb08cbbe76");

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
        "cfg-request:2faf8b36a3b8af1ec2ace43a6c86351ed83a0376901e032943d90d408dfb732d",
        "cfg-request:8a711c275d1318027253aaa8287f764995b9deb857211898804f7f6e4d666121",
        "cfg-request:ebef6bd09cd68b785f457b5bc0d09f3aa7f1d803e02463c07d2faf562eff8e56",
        "cfg-request:2b50424fb4758d2f2c5f9c37559c1672d228863637d9a7cf3a755127dde09460",
        "cfg-request:37a4cd18e006814fc48ffd30227589d6eadbe2eedea3fbe949b8a5d52b408be1");

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
