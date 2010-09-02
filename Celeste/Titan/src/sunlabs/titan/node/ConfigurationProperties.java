/*
 * Copyright 2007-2010 Oracle. All Rights Reserved.
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
 * Please contact Oracle Corporation, 500 Oracle Parkway, Redwood Shores, CA 94065
 * or visit www.oracle.com if you need additional information or
 * have any questions.
 */
package sunlabs.titan.node;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.JMException;

import sunlabs.asdf.jmx.JMX;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.XML.XML;

/**
 * 
 */
public class ConfigurationProperties implements Serializable {
    private final static long serialVersionUID = 1L;
    
    private String[] jmxPrefix;
    private String jmxDomain;
    private File saveFile;
    public Map<String,Variable> variables;
    private MBeanServer mbs;
    
    public static interface VariableMBean extends Serializable {
        public void setValue(Object value);

        public String getName();
        
        public String getValueAsString();
    }
    
    public static class Variable implements VariableMBean {
        private final static long serialVersionUID = 1L;
        
        protected final ConfigurationProperties configuration;
        protected final String name;
        protected Object value;
        protected Object defaultValue;
        
        public Variable(ConfigurationProperties configuration, String variableName, Object defaultValue) {
            this.configuration = configuration;
            this.name = variableName;
            this.defaultValue = defaultValue;
            this.configuration.setVariable(this, defaultValue);
            try {
                this.configuration.mbs.registerMBean(this, JMX.objectName(configuration.jmxDomain, this.configuration.jmxPrefix, variableName));
            } catch (JMException e) {
                e.printStackTrace();
            }
        }
        
        public void setValue(Object value) {
            this.value = value;
            this.configuration.save();
        }
        
        public String getName() {
            return this.name;
        }

        public Boolean getValueAsBoolean() {
            return Boolean.valueOf(this.value.toString());
        }
        
        public String getValueAsString() {
            return this.value.toString();
        }
        
        public Integer getValueAsInteger() {
            return new Integer(this.getValueAsString());
        }
        
        public Long getValueAsLong() {
            return new Long(this.getValueAsString());
        }
        
        public int getInt() {
            return this.getValueAsInteger().intValue();
        }
        
        public long getLong() {
            return this.getValueAsLong().longValue();
        }
        
        public String toString() {
            return this.name + "=" + this.value.toString();
        }
    }
    
    /**
     * Create a ConfigurationProperties instance containing a set of JMX managed variables
     * which is backed by the file {@code fileName} and
     * which uses the JMX domain {@code jmxDomain} and
     * the array of Strings {@code namePrefix} as a prefix to the JMX object names
     * of every variable available in this instance.
     * 
     * The backing file {@code fileName} does not prefix the names of variables with the JMX domain or prefix.
     * 
     * @param fileName
     * @param jmxDomain
     * @param jmxPrefix
     */
    public ConfigurationProperties(String fileName, String jmxDomain, String... jmxPrefix) {
        this.saveFile = new File(fileName);
        this.jmxDomain = jmxDomain;
        this.jmxPrefix = jmxPrefix;
        this.mbs = ManagementFactory.getPlatformMBeanServer();
        this.variables = new Hashtable<String,Variable>();
        this.load(this.saveFile);
    }
    
    public Variable newVariable(String variableName, Object defaultValue) {
        Variable variable = new Variable(this, variableName, defaultValue);
        this.variables.put(variable.name, variable);
        return variable;
    }

    public void load(File file) {
        FileInputStream is = null;
        try {
            is = new FileInputStream(file);
            Properties props = new Properties();
            props.load(is);
            this.jmxDomain = props.getProperty("CONFIGURATION_PROPERTIES_DOMAIN", ConfigurationProperties.class.getName());
            props.remove("CONFIGURATION_PROPERTIES_DOMAIN");
            for (Object name: props.keySet()) {
                String value = props.getProperty(name.toString());
                if (this.variables.containsKey(name)) {
                    Variable variable = this.variables.get(name);
                    variable.setValue(value);
                } else {
                    this.variables.put(name.toString(), new Variable(this, name.toString(), value));
                }
            }
        } catch (FileNotFoundException e) {
            /**/
        } catch (IOException e) {
            /**/
        } finally {
            if (is != null) try { is.close(); } catch (IOException ignore) { /**/ }
        }
    }

    public void save(File file) {
        Properties props = new Properties();
        props.setProperty("CONFIGURATION_PROPERTIES_DOMAIN", this.jmxDomain);
        for (String name: this.variables.keySet()) {
            props.setProperty(name, this.variables.get(name).getValueAsString());
        }
        
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(file);
            props.store(os, "");
            os.close();
        } catch (FileNotFoundException e) {
            System.err.println("Cannot save ConfigurationProperties.");
        } catch (IOException e) {
            System.err.println("Cannot save ConfigurationProperties.");
        } finally {
            if (os != null) try { os.close(); } catch (IOException ignore) { /**/ }
        }
    }

    public void save() {
        this.save(this.saveFile);
    }
    
    public void setVariable(Variable variable, Object value) {
        variable.value = value;
        this.variables.put(variable.name, variable);
        this.save();
    }
    
    public Variable getVariable(String name, Object defaultValue) {
        Variable variable = this.variables.get(name);
        return variable == null ? this.newVariable(name, defaultValue) : variable;
    }
    
    public Variable getVariable(String name) {
        return this.variables.get(name);
    }
    
    public String toXHTML() {
        XHTML.Table.Body tbody = new XHTML.Table.Body();
        
        for (Map.Entry<String,Variable> entry: this.variables.entrySet()) {
            tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(entry.getKey().toString()),
                    new XHTML.Table.Data(entry.getValue().toString())));
        }
        return new XHTML.Table(new XML.Attr("class", "configurationProperties")).add(tbody).toString();
    }
    
    public static void main(String[] args) {

        ConfigurationProperties variables = new ConfigurationProperties("configuration-properties", "com.sun.sunlabs.titan");
        
        Variable variable = variables.newVariable("test/node/test", "intial value");
        
        variables.setVariable(variable, "second value");
        System.out.println("variable: " + variable.getName() + " " + variable.getValueAsString());

        variable.setValue("third value");
        System.out.println("variable: " + variable.getName() + " " + variable.getValueAsString());
        
        Variable v = variables.getVariable("foo", "default foo value");
        System.out.println("variable: " + v.getName() + " " + v.getValueAsString());
    }
}
