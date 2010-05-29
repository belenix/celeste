<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:beehive="http://labs.oracle.com/Beehive/Version1" exclude-result-prefixes="beehive">
<xsl:output method="html" doctype-system="http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd" doctype-public="-//W3C//DTD XHTML 1.0 Strict//EN" indent="yes"/>
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
 <!--
  This XSL template translates a node routing table into a complete XHTML document
  This uses Dojo 1.4.1
  -->
    <xsl:include href="copyright.xsl"/>
    <xsl:include href="dojo-head.xsl"/>
    <xsl:include href="object-store-base.xsl"/>
    
    <xsl:template match="/">
        <html lang="en" xml:lang="en" xmlns="http://www.w3.org/1999/xhtml">
           <head>
             <xsl:call-template name="dojo-head" />
             <link href="/css/BeehiveStyle.css" media="all" rel="stylesheet" type="text/css"/>
             <link href="/css/BeehiveColours-black.css" media="all" rel="stylesheet" type="text/css"/>
             <script src="/js/DOLRScript.js" type="text/javascript"> </script>
             <title>Node</title>
           </head>
           <body class="tundra">
               <img src="images/background.jpg" height="100%" width="100%" id="bgimage" style="position: absolute; top: 0px; left: 0px; width: 100%; height: 100%; z-index: -100; "/>
               <xsl:apply-templates/>
               <xsl:call-template name="copyright" />
          </body>
        </html>
    </xsl:template>
    
    <xsl:template match="beehive:node">
        <div><xsl:value-of select="@uptime"/></div>
        <xsl:apply-templates select="beehive:routing-table" />
        <xsl:apply-templates select="beehive:services" />
        <xsl:apply-templates select="beehive:object-store" />
    </xsl:template>
    
    <xsl:template match="beehive:services">
      <div class="section">
        <table id="applications">
          <caption>Node Services</caption>
          <xsl:apply-templates select="beehive:service" />
        </table>
      </div>
    </xsl:template>

    <xsl:template match="beehive:service">
        <tr><td title="{@description}"><a class="serviceName" href="service/{@name}"><xsl:value-of select="@name" /></a></td><td><xsl:value-of select="." /></td></tr>
    </xsl:template>
    
    
    <!--  this is taken from beehive-route-table.xsl -->
    <xsl:template match="beehive:routing-table">
      <div class="section">
      <table class="neighbour-map">
        <caption><xsl:value-of select="@objectId"/></caption>
        <thead>
        <tr><th><!-- empty cell--></th>
          <xsl:call-template name="header-loop">
            <xsl:with-param name="i">0</xsl:with-param>
            <xsl:with-param name="count"><xsl:value-of select="@depth - 1" /></xsl:with-param>
          </xsl:call-template>
          </tr>
        </thead>
        <tbody>
          <xsl:call-template name="row-loop">
            <xsl:with-param name="i">0</xsl:with-param>
            <xsl:with-param name="count">16</xsl:with-param>
          </xsl:call-template>
        </tbody>
      </table>
      </div>
    </xsl:template>

    <xsl:template match="beehive:route">
        <xsl:apply-templates select="beehive:route-node" />
    </xsl:template>
    
    <xsl:template match="beehive:route-node">
      <a class="NodeId" href="http://{@ipAddress}:{@port}"><xsl:value-of select="@objectId"></xsl:value-of></a><br/>
    </xsl:template>
    
    <!-- Loop through the columns, creating a header for each -->
    <xsl:template name="header-loop">
      <xsl:param name="i" />
      <xsl:param name="count" />
      <xsl:if test="$i &lt;= $count">
        <th><xsl:value-of select="$i"></xsl:value-of></th>
      </xsl:if>

      <!-- call this template again, recursively. -->
      <xsl:if test="$i &lt;= $count">
        <xsl:call-template name="header-loop">
          <xsl:with-param name="i"><xsl:value-of select="$i + 1" /></xsl:with-param>
          <xsl:with-param name="count"><xsl:value-of select="$count" /></xsl:with-param>
        </xsl:call-template>
      </xsl:if>
    </xsl:template>
      
    <!-- Loop through the rows, invoking the column-loop for each row -->
    <xsl:template name="row-loop">
      <xsl:param name="i" />
      <xsl:param name="count" />
      <xsl:if test="$i &lt;= $count">
        <tr>
        <td><xsl:value-of select="$i"></xsl:value-of></td>
        <xsl:call-template name="column-loop">
              <xsl:with-param name="i">0</xsl:with-param>
              <xsl:with-param name="count"><xsl:value-of select="@depth - 1" /></xsl:with-param>
              <xsl:with-param name="row"><xsl:value-of select="$i"/></xsl:with-param>
        </xsl:call-template>
        </tr>
      </xsl:if>

      <!-- call this template again, recursively. -->
      <xsl:if test="$i &lt;= $count">
        <xsl:call-template name="row-loop">
          <xsl:with-param name="i"><xsl:value-of select="$i + 1" /></xsl:with-param>
          <xsl:with-param name="count"><xsl:value-of select="$count" /></xsl:with-param>
        </xsl:call-template>
      </xsl:if>
    </xsl:template>
    
    <xsl:template name="column-loop">
      <xsl:param name="i" />
      <xsl:param name="count" />
      <xsl:param name="row" />
      <xsl:if test="$i &lt;= $count">
        <xsl:variable name="nroutes"><xsl:value-of select="count(beehive:route[@row=$row and @col=$i]/beehive:route-node)"></xsl:value-of></xsl:variable>
        <xsl:choose>
          <xsl:when test="$nroutes = 0">
            <td id="cell-{$row}-{$i}"><xsl:text disable-output-escaping='yes'>&#160;</xsl:text></td></xsl:when>
          <xsl:otherwise>
            <td id="cell-{$row}-{$i}" class="full">
              <xsl:value-of select="$nroutes"></xsl:value-of>
              <div class="neighbour" connectId="cell-{$row}-{$i}" dojoType="sunlabs.StickyTooltip">
                <xsl:apply-templates select="beehive:route[@row=$row and @col=$i]" />
              </div>
            </td>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:if>

      <!-- call this template again, recursively. -->
      <xsl:if test="$i &lt;= $count">
        <xsl:call-template name="column-loop">
          <xsl:with-param name="i"><xsl:value-of select="$i + 1" /></xsl:with-param>
          <xsl:with-param name="count"><xsl:value-of select="$count" /></xsl:with-param>
          <xsl:with-param name="row"><xsl:value-of select="$row" /></xsl:with-param>
        </xsl:call-template>
      </xsl:if>
    </xsl:template>
</xsl:stylesheet>
