/*
 * Copyright 2007-2010 Sun Microsystems, Inc. All Rights Reserved.
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
package sunlabs.asdf.web.jmx;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.LinkedList;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * Helper code to work with the JMX implementation.
 * 
 * @author Glenn Scott
 *
 */
/*
 * Don't forget to turn on JMX in the VM at run-time:
 * 
 * -Xmx1024m -ea 
 * -Dcom.sun.management.jmxremote.port=17000
 * -Dcom.sun.management.jmxremote.password.file=/Users/glennscott/Development/Celeste/storage/Celeste/etc/jmxremote.password.template
 * -Dcom.sun.management.jmxremote
 */
public class JMX implements JMXMBean {
    private String message;

    public JMX() {
        this.message = "hello world";
    }
    
    public void jmxSetMessage(String message) {
        this.message = message;
    }

    public String jmxGetMessage() {
        return this.message;
    }

    public void jmxSayHello() {
        System.out.println("hello");
    }
  
    /**
     * Given an ObjectName, produce a file path-name that corresponds
     * to the values in the ObjectName in the order they appear.
     * 
     * @param objectName
     */
    public static String objectNameToFileName(ObjectName objectName) {
        LinkedList<String> path = JMX.linkedListOfValues(objectName);
        
        StringBuilder result = new StringBuilder(objectName.getDomain());
        for (String name : path) {
            result.append(File.separator);
            result.append(name);
        }
        return result.toString();
    }
    
    /**
     * Produce a LinkedList of the values of an ObjectName or another
     * Object (as according to the toString() method)
     * or an array containing any combination of ObjectName and Object.
     * 
     * The order the values is the same as the appear.
     * @param object
     */
    public static LinkedList<String> linkedListOfValues(Object...object) {
        LinkedList<String> result = new LinkedList<String>();
        // flatten out the pathname
        for (Object value : object) {
            if (value.getClass().isArray()) {
                for (Object o : (Object[]) value) {
                    result.addAll(linkedListOfValues(o));
                }
            } else if (value instanceof ObjectName) {
                String[] tokens = value.toString().split("[:,]");

                for (int i = 1; i < tokens.length; i++) {
                    String[] avPair = tokens[i].split("=");
                    result.add(avPair[1]);
                }
            } else {
                result.add(value.toString());
            }
        }

        return result;
    }
    
    /**
     * Compose an ObjectName with the given domain name and String
     * representations of each path component.
     * 
     * See {@code linkedListOfValues}
     * 
     * @param domain
     * @param pathName
     * @return A new ObjectName instance.
     * @throws MalformedObjectNameException
     */
    public static ObjectName objectName(String domain, Object... pathName) throws MalformedObjectNameException {
        LinkedList<String> list = linkedListOfValues(pathName);

        StringBuilder name = new StringBuilder(domain);

        String separator = ":";

        int index = 0;
        for (String value : list) {
            name.append(separator).append("p").append(index).append("=").append(value.replaceAll(":", "."));
            index++;
            separator = ",";
        }
        try {
            return new ObjectName(name.toString());
        } catch (MalformedObjectNameException e) {
            System.out.println("Offending JMX.objectName: " + name.toString());
            throw e;
        }
    }
    
    public static ObjectName objectName(ObjectName root, Object... pathName) throws MalformedObjectNameException {
        return JMX.objectName(root.getDomain(), root, pathName);
    }
    
    public static void main(String[] args) throws MalformedObjectNameException, IOException {
        try {
            String domain = "mydomain";
            String[] prefix = { "1234567890", "abc" };

            ObjectName root = JMX.objectName(domain, "sunlabs", "beehive", "dolr");
            System.out.println("root: " + root);
            ObjectName beanName = JMX.objectName(root, prefix, "foo");

            System.out.println("string: " + beanName.toString());

            // Get the platform MBeanServer
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            JMX bean = new JMX();
            try {
                // Uniquely identify the MBeans and register them with the platform MBeanServer 
                mbs.registerMBean(bean, beanName);
            } catch(Exception e) {
                e.printStackTrace();
            }
            
            System.out.println("file Name: " + JMX.objectNameToFileName(beanName));

            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
