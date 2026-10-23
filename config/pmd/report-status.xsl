<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:pmd="http://pmd.sourceforge.net/report/2.0.0">
  <xsl:output method="text" encoding="UTF-8" />
  <xsl:template match="/">
    <xsl:if test="not(pmd:pmd)"><xsl:message terminate="yes">Expected the native PMD report.</xsl:message></xsl:if>
    <xsl:text>pmd.processingErrors=</xsl:text><xsl:value-of select="count(pmd:pmd/pmd:error)" /><xsl:text>&#10;</xsl:text>
    <xsl:text>pmd.configurationErrors=</xsl:text><xsl:value-of select="count(pmd:pmd/pmd:configerror)" /><xsl:text>&#10;</xsl:text>
  </xsl:template>
</xsl:stylesheet>
