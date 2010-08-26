<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:titan="http://labs.oracle.com/Titan/Version1" exclude-result-prefixes="titan">
  <xsl:output method="html" doctype-system="http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd" doctype-public="-//W3C//DTD XHTML 1.0 Strict//EN" indent="yes"/>
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
               <img src="images/sun_labs_123x65.gif" />
               <xsl:apply-templates />
               <xsl:call-template name="copyright" />
          </body>
        </html>
    </xsl:template>
    
    
</xsl:stylesheet>