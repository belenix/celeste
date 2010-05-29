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
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:beehive="http://labs.oracle.com/Beehive/Version1"
  exclude-result-prefixes="beehive">
  <xsl:template name="dojo-head">
    <script type="text/javascript">djConfig={ isDebug: true, parseOnLoad: true, baseUrl: './', useXDomain: true, modulePaths: {'sunlabs': '/dojo/1.3.1/sunlabs'}};</script>
    <link href="http://o.aolcdn.com/dojo/1.4.1/dojo/resources/dojo.css" media="all" rel="stylesheet" type="text/css" />
    <link href="http://o.aolcdn.com/dojo/1.4.1/dijit/themes/tundra/tundra.css" media="all" rel="stylesheet" type="text/css" />
    <script src="http://o.aolcdn.com/dojo/1.4.1/dojo/dojo.xd.js" type="text/javascript"></script>
    <script type="text/javascript">
dojo.require("dojo.parser");
dojo.require("sunlabs.StickyTooltip");
    </script>
  </xsl:template>
</xsl:stylesheet>
