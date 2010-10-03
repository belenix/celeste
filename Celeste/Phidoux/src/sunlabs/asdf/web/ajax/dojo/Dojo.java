/*
 * Copyright 2007-2008 Sun Microsystems, Inc. All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 16 Network Circle, Menlo
 * Park, CA 94025 or visit www.sun.com if you need additional
 * information or have any questions.
 */
package sunlabs.asdf.web.ajax.dojo;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;
import sunlabs.asdf.web.XML.Xxhtml;

/**
 *
 * A Java front-end to using Dojo with the sunlabs.asdf.xhtml package.
 *
 * This class can be used in a way that turns it on or off by setting two runtime parameters.
 * Otherwise, the class can be used directly without configuability.
 *
 * The use the class configurably, set the System parameters
 * {@code sunlabs.asdf.xhtml.ajax.dojo.base}
 * to the URL of the Dojo installation and the parameter
 * {@code sunlabs.asdf.xhtml.ajax.dojo.dojo} to the path of the Dojo script to load.
 *
 *
 * For example to use Dojo via AOL and the cross-domain version of the Dojo platform,
 * specify {@code -Dsunlabs.asdf.xhtml.ajax.dojo.base=http://o.aolcdn.com/dojo/1.1.1}
 * and
 * {@code -Dsunlabs.asdf.xhtml.ajax.dojo.dojo=dojo/dojo.xd.js}
 *
 * To load, for example, modules implementing the
 * {@code sunlabs}
 * set of extensions local (cross-domain) to your webserver, include a configuration string like this:
 * <code>"isDebug: true, parseOnLoad: true, useXDomain: true, modulePaths: {'sunlabs': '/web/dojo/1.1.1/sunlabs'}";</code>
 *
 * To use Dojo via a local installation of the Dojo platform, specify
 * {@code -Dsunlabs.asdf.xhtml.ajax.dojo.base=/web/dojo/1.1.1 -Dsunlabs.asdf.xhtml.ajax.dojo.dojo=dojo/dojo.js}
 *
 * @author Glenn Scott
 *
 */
public class Dojo {
    private static String DOJO_ROOT_PROPERTY_NAME = "sunlabs.asdf.xhtml.ajax.dojo.rootURL";
    private static String DOJO_JS_PROPERTY_NAME = "sunlabs.asdf.xhtml.ajax.dojo.dojoJs";
    private static String DOJO_THEME_PROPERTY_NAME = "sunlabs.asdf.xhtml.ajax.dojo.theme";

    private URI dojoRoot;
    private String dojoJs;
    private String djConfig;
    private String themeName;
    private List<String> requires;
    private List<String> onLoad;
    private Map<String,String> modules;

    /**
     * Create a Dojo object instance with the default root URI for the Dojo sources
     * take from the System property named by {@link #DOJO_ROOT_PROPERTY_NAME},
     * and the relative path specified by the System property {@link #DOJO_JS_PROPERTY_NAME},
     * and the Dojo theme specified by the System property {@link #DOJO_JS_PROPERTY_NAME} (default is {@code "tundra"}).
     *
     * <p>
     * If the value of {@code #DOJO_ROOT_PROPERTY_NAME} cannot be parsed as a {@link URI}
     * it is assigned to {@code null} and subsequent use of this Dojo instance will be inert.
     * </p>
     * @see Dojo#setRootURI(URI)
     * @see Dojo#setDojoJsPath(String)
     * @see Dojo#setTheme(String)
     */
    public Dojo(String dojoRoot, String dojoJavaScript, String dojoTheme) {
        this.requires = new LinkedList<String>();
        this.onLoad = new LinkedList<String>();
        this.modules = new HashMap<String,String>();

        this.themeName = dojoTheme;
        this.dojoJs = dojoJavaScript;

        try {
            this.dojoRoot = new URI(dojoRoot);
        } catch (URISyntaxException e) {
            this.dojoRoot = null;
        } catch (NullPointerException e) {
            this.dojoRoot = null;
        }
    }

    public boolean isEnabled() {
        return (this.dojoRoot == null || this.dojoJs == null) ? false : true;
    }

    public void setRootURI(URI root) {
        this.dojoRoot = root;
    }

    public void setDojoJsPath(String path) {
        this.dojoJs = path;
    }

    public void setTheme(String themeName) {
        this.themeName = themeName;
    }

    public Dojo addModule(String name, String path) {
        this.modules.put(name, path);
        return this;
    }

    public Dojo setConfig(String config) {
        this.djConfig = config;
        return this;
    }

    public Dojo requires(String...requirements) {
        for (String requirement : requirements) {
            this.requires.add(requirement);
        }
        return this;
    }

    public XHTML.Script getRequires() {
        if (this.isEnabled()) {
            StringBuilder cdata = new StringBuilder();
            for (String m : this.modules.keySet()) {
                cdata.append(String.format("dojo.registerModulePath(\"%s\", \"%s\");\n", m, this.modules.get(m)));
            }

            for (String s : this.requires) {
                cdata.append("dojo.require(\"").append(s).append("\");\n");
            }
            return new XHTML.Script("text/javascript").add(new XHTML.CDATA(cdata.toString()));
        }
        return null;
    }

    public XHTML.Script initialise() {
        if (this.isEnabled()) {
            XHTML.Script result = new XHTML.Script("text/javascript").setSource(this.dojoRoot + "/" + this.dojoJs);
            if (this.djConfig != null) {
                result.addAttribute(new XML.Attr("djConfig", this.djConfig));
            }
            return result;
        }
        return null;
    }

    public XHTML.Link theme() {
        if (this.isEnabled()) {
            return Xxhtml.Stylesheet("%s/dijit/themes/%2$s/%2$s.css", this.dojoRoot, this.themeName);
        }
        return null;
    }

    public XHTML.Link style() {
        if (this.isEnabled()) {
            return Xxhtml.Stylesheet("%s/dojo/resources/dojo.css", this.dojoRoot);
        }
        return null;
    }

    public String getTheme() {
        return this.themeName;
    }

    public void addOnLoad(String cdata) {
        this.onLoad.add(cdata);
    }

    public XHTML.Head doConfig(XHTML.Head head) {
        XHTML.Script djConfigScript = new XHTML.Script("text/javascript").add("djConfig={ ").add(this.djConfig).add("};");
        head.add(djConfigScript);
        return head;
    }

    public XHTML.Head doLoad(XHTML.Head head) {
        /*
        <link href="http://o.aolcdn.com/dojo/1.3.1/dojo/resources/dojo.css" media="all" rel="stylesheet" type="text/css"/>
        <link href="http://o.aolcdn.com/dojo/1.3.1/dijit/themes/tundra/tundra.css" media="all" rel="stylesheet" type="text/css"/>
           <script type="text/javascript">
                djConfig={
                  parseOnLoad: true,
                  isDebug: true,
                  baseUrl: "./",
                  modulePaths: { sunlabs: "/dojo/1.1.1/sunlabs" }
                };
            </script>
        <script  src="http://o.aolcdn.com/dojo/1.3.1/dojo/dojo.xd.js" type="text/javascript"></script>
        */

        XHTML.Link dojoCss = Xxhtml.Stylesheet("%s/dojo/resources/dojo.css", this.dojoRoot);
        XHTML.Link themeCss =  Xxhtml.Stylesheet("%s/dijit/themes/%2$s/%2$s.css", this.dojoRoot, this.themeName);
        XHTML.Script dojoScript = new XHTML.Script("text/javascript").setSource(this.dojoRoot + "/" + this.dojoJs);
        head.add(dojoCss).add(themeCss).add(dojoScript);
        return head;
    }

    public XHTML.Head doFini(XHTML.Head head) {
        /*
         * <script type="text/javascript">/ *<![CDATA[* /
         * dojo.require("dojo.parser");
         * dojo.require("sunlabs.StickyTooltip");
         *
         * /*]]>* /</script>
         */

        if (this.isEnabled()) {
            StringBuilder cdata = new StringBuilder();
            for (String m : this.modules.keySet()) {
                cdata.append(String.format("dojo.registerModulePath(\"%s\", \"%s\");\n", m, this.modules.get(m)));
            }

            for (String s : this.requires) {
                cdata.append("dojo.require(\"").append(s).append("\");\n");
            }
            if (this.onLoad.size() > 0) {
                for (String func : this.onLoad) {
                    cdata.append("dojo.addOnLoad(" + func + ");\n");
                }
            }

            head.add(new XHTML.Script("text/javascript").add(new XHTML.CDATA(cdata.toString())));
        }

        return head;
    }

    /**
     * Given an {@link sunlabs.asdf.web.XML.XHTML.Head XHTML.Head} instance, add to it the necessary elements to configure, load and make ready, Dojo.
     * @param head
     */
    public void dojoify(XHTML.Head head) {
        this.doConfig(head);
        this.doLoad(head);
        this.doFini(head);
    }

    public static void main(String[] args) throws Exception {
        System.setProperty(Dojo.DOJO_ROOT_PROPERTY_NAME, "http://o.aolcdn.com/dojo/1.3.1");
        System.setProperty(Dojo.DOJO_JS_PROPERTY_NAME, "dojo/dojo.xd.js");


        Dojo dojo = new Dojo(System.getProperty(Dojo.DOJO_ROOT_PROPERTY_NAME, "http://o.aolcdn.com/dojo/1.3.1"),
        		System.getProperty(Dojo.DOJO_JS_PROPERTY_NAME, "dojo/dojo.xd.js"),
        		System.getProperty(Dojo.DOJO_THEME_PROPERTY_NAME, "tundra"));
        dojo.setConfig("isDebug: true, parseOnLoad: true, baseUrl: './', modulePaths: { sunlabs: '/dojo/1.3.1/sunlabs' }");
        dojo.requires("dojo.parser", "sunlabs.StickyTooltip");
        dojo.addOnLoad("function() { tableStripe('objectStore', '#ecf3fe', '#ffffff'); tableStripe('applications', '#ffffff', '#ecf3fe'); }");

        XHTML.Head head = new XHTML.Head();

        dojo.dojoify(head);
        System.out.printf("%s%n", head.toString());

    }
}
