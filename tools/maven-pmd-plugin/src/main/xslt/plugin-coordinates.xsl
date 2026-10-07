<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" encoding="UTF-8" />
  <xsl:param name="groupId" />
  <xsl:param name="artifactId" />
  <xsl:param name="version" />
  <xsl:template match="@*|node()">
    <xsl:copy><xsl:apply-templates select="@*|node()" /></xsl:copy>
  </xsl:template>
  <xsl:template match="/plugin/groupId">
    <groupId><xsl:value-of select="$groupId" /></groupId>
  </xsl:template>
  <xsl:template match="/plugin/artifactId">
    <artifactId><xsl:value-of select="$artifactId" /></artifactId>
  </xsl:template>
  <xsl:template match="/plugin/version">
    <version><xsl:value-of select="$version" /></version>
  </xsl:template>
</xsl:stylesheet>
