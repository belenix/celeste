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
package sunlabs.beehive.node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

import sunlabs.asdf.web.XML.XHTML;
import sunlabs.asdf.web.http.HTTP;
import sunlabs.beehive.api.XHTMLInspectable;

/**
 * Find a clever way to encode virtue.
 * 
 * So here goes: A Reputation is a vector of attributes of behaviour.
 * The Reputation stores the actual values of each of the attributes
 * as well as the same attribute scaled between 0.0 (worst) to 1.0 (best)
 * 
 * Currently, the vector is a fixed set of behaviours.
 * 
 * Every DOLR Node keeps a Repuation instance for every neighbour it has interacted with.
 */
public final class Reputation implements XHTMLInspectable, Serializable {
    private final static long serialVersionUID = 1L;
    private final static int maximum = 100;
	
    private Map<String,Virtue> virtues;
    
    
    public static final class Virtue implements Serializable {
    	private final static long serialVersionUID = 1L;

        private int bias;
        private int value;
        
        public Virtue(int bias) {
            this.value = Reputation.maximum / 2;
            this.bias = Math.min(bias, Reputation.maximum);
            this.bias = Math.max(this.bias, 1);
        }
        
        /**
         * 
         */
        public int success() {
            this.value = this.value + (Reputation.maximum - this.value) / this.bias;
            return this.value;
        }
        
        /**
         *
         */
        public int failure() {
            this.value = this.value - (this.value) / this.bias;
            return this.value;
        }
        
        public int getValue() {
            return this.value;
        }

        public void setValue(int v) {
            this.value = v;
        }
        
        public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
            return new XHTML.Span(String.format("%3d (*%d)", this.value, this.bias));
        }
        
        public String toString() {
            return String.format("value=%d bias=%d", this.value, this.bias);
        }
    }

    public static Map<String,Integer> newCoefficients() {
        Map<String,Integer> v = new Hashtable<String,Integer>();
        v.put(Reputation.LATENCY, new Integer(0));
        v.put(Reputation.ROUTING, new Integer(0));
        v.put(Reputation.PUBLISHER, new Integer(0));
        v.put(Reputation.VALIDNODEID, new Integer(0));
        v.put(Reputation.AVAILABLE, new Integer(0));
        return v;
    }

    public static final String LATENCY = "Latency";
    public static final String ROUTING = "Routing";
    public static final String PUBLISHER = "Publisher";
    public static final String VALIDNODEID = "Valid";
    public static final String AVAILABLE = "Available";

    /**
     * 
     */
    public Reputation() {
        super();
        this.virtues = new Hashtable<String,Virtue>();
        this.virtues.put(Reputation.LATENCY, new Virtue(5));
        this.virtues.put(Reputation.ROUTING, new Virtue(5));
        this.virtues.put(Reputation.PUBLISHER, new Virtue(5));
        this.virtues.put(Reputation.VALIDNODEID, new Virtue(5));
        this.virtues.put(Reputation.AVAILABLE, new Virtue(5));
    }
    
    /**
     * 
     * @param v
     * @param value
     */
    public void put(String v, int value) {
        Virtue a = this.virtues.get(v);
        a.setValue(value);
    }

    /**
     * 
     * @param v
     * @return the reputation virtue indexed by <code>index</code>.
     */
    public Virtue get(String v) {
        return this.virtues.get(v);
    }

    /**
     * 
     * @param v The reputation virture to award.
     * @return The updated success/fail score.
     */
    public int success(String v) {
        Virtue a = this.virtues.get(v);
        if (a != null)
            return a.success();
        return 0;
    }

    /**
     * 
     * @param v The reputation virture to punish.
     * @return The updated success/fail score.
     */
    public int failure(String v) {
        Virtue a = this.virtues.get(v);
        if (a != null)
            return a.failure();
        return 0;
    }

    /**
     * Compute the reputation by applying the weighted coefficients to the set of virtues.
     * The coefficients are supplied in a Map with keys taken from the Reputation.Virtue keyspace.
     * 
     * @param coefficients
     * @return A value between 0 and 10 representing the weighted aggregate of virtues.
     */
    public int compute(Map<String,Integer> coefficients) {
        int sum = 0;

        // Calculate the scale of the coeffiecients here.
        int weightSum = 0;
        for (Map.Entry<String,Integer> entry : coefficients.entrySet()) {
            weightSum += entry.getValue();
        }
        double scale = (double) Reputation.maximum / weightSum;
        
        // Compute the reputation based upon the coefficients.
        for (Map.Entry<String,Integer> entry : coefficients.entrySet()) {
            Integer weight = entry.getValue();
            Virtue virtue = this.virtues.get(entry.getKey());

            sum += (weight * scale) * virtue.getValue();
            weightSum += weight;
        }
        
        return sum / 100;
    }

    public XHTML.EFlow toXHTML(URI uri, Map<String,HTTP.Message> props) {
        XHTML.Table.Body tbody = new XHTML.Table.Body();

        for (Map.Entry<String,Virtue> entry: this.virtues.entrySet()) {
            tbody.add(new XHTML.Table.Row(new XHTML.Table.Data(entry.getKey()),
                    new XHTML.Table.Data(entry.getValue().toXHTML(uri, props))));
        }

        return new XHTML.Table(tbody);
    }

    public String toString() {
        new Throwable().printStackTrace();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Properties props = new Properties();

        for (String key : this.virtues.keySet()) {
            props.setProperty(key, this.virtues.get(key).toString());
        }
        try {
            props.store(os, "Reputation");
        } catch (IOException e) {
            return "";
        }

        return new String(os.toByteArray());
    }
    
    public static void main(String[] args) throws Exception {
        Reputation r = new Reputation();
        System.out.println(r.toString());
        r.success(Reputation.ROUTING);
        r.failure(Reputation.PUBLISHER);

        Map<String,Integer> coefficients = Reputation.newCoefficients();

        coefficients.put(Reputation.LATENCY, new Integer(50));
        coefficients.put(Reputation.PUBLISHER, new Integer(25));
        coefficients.put(Reputation.ROUTING, new Integer(25));
        r.compute(coefficients);        
    }
}
