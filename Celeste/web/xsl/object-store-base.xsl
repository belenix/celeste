<?xml version="1.0" encoding="UTF-8"?>
<!--
 * Copyright 2004-2010 Oracle. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 *
 * This code is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * only, as published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is
 * included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA
 *
 * Please contact Oracle., 16 Network Circle, Menlo Park, CA 94025
 * or visit www.oracle.com if you need additional
 * information or have any questions.
 -->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:beehive="http://labs.oracle.com/Beehive/Version1" exclude-result-prefixes="beehive">
    <xsl:variable name="column">0</xsl:variable>
    <!-- The value of a time that will never be reached (infinite time) (java.lang.Long.MAX_VALUE). -->
    <xsl:variable name="infinite-time">9223372036854728568</xsl:variable>
    
	<xsl:template match="beehive:object-store-">
      <div class="section">
        <table class="objectStore">
          <caption>Object Store</caption>
          <xsl:apply-templates select="beehive:object" />
        </table>
      </div>
	</xsl:template>
  
    <xsl:template match="beehive:object-">
    <tr><td class="objectId"><a href="inspect/{@objectId}" title="{@objectType}"><xsl:value-of select="@objectId"/></a></td>
        <td><xsl:value-of select="@size"/></td>
        <td><xsl:value-of select="@replication"/></td>
        <td><xsl:value-of select="@ttl"/></td>
    </tr>
    </xsl:template>
    
    <xsl:template match="beehive:object-store">
      <xsl:variable name="nobjects"><xsl:value-of select="count(beehive:object)"></xsl:value-of></xsl:variable>
      <xsl:message><xsl:value-of select="$nobjects"/></xsl:message>
      <xsl:message><xsl:value-of select="$nobjects mod 2"/></xsl:message>
      
      <div class="section">
        <table class="objectStore">
          <caption>Object Store</caption>
          <!-- xsl:apply-templates select="beehive:object" /-->
          <xsl:call-template name="object-row-loop">
            <xsl:with-param name="i">1</xsl:with-param>
            <xsl:with-param name="count"><xsl:value-of select="floor($nobjects div 2)" /></xsl:with-param>
          </xsl:call-template>
          <!--  handle the residue of one, if any -->
          <xsl:if test="$nobjects mod 2 = 1"><tr><xsl:apply-templates select="beehive:object[$nobjects]"/><td></td><td></td><td></td><td></td></tr></xsl:if>          
        </table>
      </div>
    </xsl:template>
  
    <xsl:template match="beehive:object">
      <td class="objectId"><a href="inspect/{@objectId}" title="{@objectType}"><xsl:value-of select="@objectId"/></a></td>
        <td><xsl:value-of select="@size"/></td>
        <td><xsl:value-of select="@lowWater"/> &#8804; <xsl:value-of select="@nStore"/> &#8804; <xsl:value-of select="@highWater"/></td>
        <td><xsl:value-of select="@ttl"/></td>
    </xsl:template>
       
    <!-- Loop through the rows, invoking the column-loop for each row -->
    <xsl:template name="object-row-loop">
      <xsl:param name="i" />
      <xsl:param name="count" />
      <xsl:if test="$i &lt;= $count">
        <xsl:message>row-loop: i=<xsl:value-of select="$i"/><xsl:text> count=</xsl:text><xsl:value-of select="$count"/></xsl:message>
        <xsl:message>row <xsl:value-of select="$i"/>: [<xsl:value-of select="($i - 1) * 2 + 1"/>] [<xsl:value-of select="($i - 1) * 2 + 2"/>]</xsl:message>
        <tr>
         <xsl:apply-templates select="beehive:object[($i - 1) * 2 + 1]" />
         <xsl:apply-templates select="beehive:object[($i - 1) * 2 + 2]" />
        </tr>
      </xsl:if>

      <!-- call this template again, recursively. -->
      <xsl:if test="$i &lt;= $count">
        <xsl:call-template name="object-row-loop">
          <xsl:with-param name="i"><xsl:value-of select="$i + 1" /></xsl:with-param>
          <xsl:with-param name="count"><xsl:value-of select="$count" /></xsl:with-param>
        </xsl:call-template>
      </xsl:if>
    </xsl:template>
    
</xsl:stylesheet>