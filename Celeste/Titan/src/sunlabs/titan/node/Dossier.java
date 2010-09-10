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
import java.io.FilenameFilter;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import sunlabs.asdf.util.ObjectLock;
import sunlabs.asdf.web.XML.XHTML;
import sunlabs.titan.TitanGuidImpl;
import sunlabs.titan.api.TitanGuid;
public class Dossier {
    private final static long serialVersionUID = 1L;

    public final static int SCALE = 1000;

    public final static String LATENCY = "Latency";
    public static final String ROUTING = "Routing";
    public static final String PUBLISHER = "Publisher";
    public static final String VALIDNODEID = "Valid";
    public static final String AVAILABLE = "Available";

    public static class ProbabilityThing implements Serializable {
        private static final long serialVersionUID = 1L;

        private double count;
        private double successes;

        public ProbabilityThing() {
            this.count = 0.0;
            this.successes = 0.0;
        }

        protected void success() {
            this.count++;
            this.successes++;
        }

        protected void failure() {
            this.count++;
        }

        public double probabilityOfSuccess() {
            return this.successes / this.count;
        }

        public double probabilityOfFailure() {
            return 1.0 - this.probabilityOfSuccess();
        }

        public double getCount() {
            return count;
        }

        public double getSuccesses() {
            return successes;
        }

        @Override
        public String toString() {
            return Double.toString(this.probabilityOfSuccess());
        }
    }

    public static class AverageThing implements Serializable {
        private final static long serialVersionUID = 1L;

        private double bias;
        private double value;

        public AverageThing(double bias) {
            this.bias = bias;
            this.value = 0.0;
        }

        /**
         *
         */
        protected double addSample(double sample) {
            this.value = (this.bias * sample)  + (1.0 - this.bias) * this.value;
            return this.value;
        }

        public double getValue() {
            return this.value;
        }

        @Override
        public String toString() {
            return Double.toString(this.value);
        }
    }

    public static class Entry implements Serializable {
        private static final long serialVersionUID = 1L;

        private NodeAddress address;
        private long timestamp;
        private String revision;

        private Map<String,ProbabilityThing> probabilities;
        private Map<String,AverageThing> averages;

        public Entry() {
            this.probabilities = new HashMap<String,ProbabilityThing>();
            this.averages = new HashMap<String,AverageThing>();
            this.averages.put(Dossier.LATENCY, new AverageThing(0.75));
        }

        public Entry(NodeAddress address) {
            this();
            this.address = address;
            this.timestamp = System.currentTimeMillis();
        }

        public NodeAddress getNodeAddress() {
            return this.address;
        }

        public Entry setTimestamp(long time) {
            this.timestamp = time;
            return this;
        }

        public Entry setTimestamp() {
            this.timestamp = System.currentTimeMillis();
            return this;
        }

        public long getTimestamp() {
            return this.timestamp;
        }

        public String getRevision() {
            return revision;
        }

        public Entry setRevision(String revision) {
            this.revision = revision;
            return this;
        }

        public ProbabilityThing getProbability(String name) {
            ProbabilityThing p = this.probabilities.get(name);
            if (p == null) {
                p = new ProbabilityThing();
                this.probabilities.put(name, p);
            }

            return p;
        }

        public AverageThing getAverage(String name) {
            AverageThing a = this.averages.get(name);
            if (a == null) {
                a = new AverageThing(0.75);
                this.averages.put(name, a);
            }
            return a;
        }

        public Entry addSample(String name, double value) {
            AverageThing a = this.getAverage(name);
            a.addSample(value);
            return this;
        }

        public Entry success(String name) {
            ProbabilityThing p = this.getProbability(name);
            p.success();
            return this;
        }

        public Entry failure(String name) {
            ProbabilityThing p = this.getProbability(name);
            p.failure();
            return this;
        }

        /**
         * On a scale of 0 - Dossier.SCALE
         *
         * @param coefficients
         */
        public int computeReputation(Map<String,Integer> coefficients) {
            int reputation = 0;

            // XXX restructure to not only take coefficients, but coefficients and expected values.

            // Condition the coefficients
            // Calculate the scale of the coeffiecients here.
            int weightSum = 0;
            for (Map.Entry<String,Integer> entry : coefficients.entrySet()) {
                weightSum += entry.getValue();
            }
            double scale = 1.0 / weightSum;

            for (String key : this.probabilities.keySet()) {
                Integer c = coefficients.get(key);
                if (c != null) {
                    ProbabilityThing p = this.probabilities.get(key);
                    reputation += p.probabilityOfSuccess() * (c * scale);
                }
            }

            return reputation;
        }

        public XHTML.Table toXHTML(Map<String,Integer> coefficients) {
            XHTML.Table.Body tbody1 = new XHTML.Table.Body();
            tbody1.add(new XHTML.Table.Row(new XHTML.Table.Data("Timestamp"), new XHTML.Table.Data(Long.toString(this.timestamp))));
            tbody1.add(new XHTML.Table.Row(new XHTML.Table.Data("CurrentTime"), new XHTML.Table.Data(Long.toString(System.currentTimeMillis()))));
            tbody1.add(new XHTML.Table.Row(new XHTML.Table.Data("Age"), new XHTML.Table.Data(Long.toString((System.currentTimeMillis() - this.timestamp)/1000) + "s")));
            XHTML.Table column1 = new XHTML.Table(tbody1);

            XHTML.Table.Body tbody2 = new XHTML.Table.Body();
            for (String key : this.averages.keySet()) {
                AverageThing a = this.getAverage(key);
                tbody2.add(new XHTML.Table.Row(new XHTML.Table.Data(key), new XHTML.Table.Data(String.format("%.3f", a.getValue()))));
            }
            XHTML.Table column2 = new XHTML.Table(tbody2);

            XHTML.Table.Body tbody3 = new XHTML.Table.Body();
            for (String key : this.probabilities.keySet()) {
                ProbabilityThing p = this.probabilities.get(key);
                tbody3.add(new XHTML.Table.Row(new XHTML.Table.Data(key), new XHTML.Table.Data(String.format("%.3f", p.probabilityOfSuccess()))));
            }
            XHTML.Table column3 = new XHTML.Table(tbody3);

            XHTML.Table.Body tbody4 = new XHTML.Table.Body();
            for (String key : coefficients.keySet()) {
                tbody4.add(new XHTML.Table.Row(new XHTML.Table.Data(key), new XHTML.Table.Data(String.format("%d", coefficients.get(key)))));
            }

            tbody4.add(new XHTML.Table.Row(new XHTML.Table.Data("Reputation"), new XHTML.Table.Data(this.computeReputation(coefficients))));
            XHTML.Table column4 = new XHTML.Table(tbody4);
            return new XHTML.Table().add(new XHTML.Table.Body(new XHTML.Table.Row(new XHTML.Table.Data(column1),
                    new XHTML.Table.Data(column2),
                    new XHTML.Table.Data(column3),
                    new XHTML.Table.Data(column4))));
        }
    }

    public BackedObjectMap<TitanGuid,Dossier.Entry> entries;

    private ObjectLock<TitanGuid> locks;

    public Dossier(String name) throws BackedObjectMap.AccessException {
        this.entries = new BackedObjectMap<TitanGuid,Dossier.Entry>(name + File.separator + "dossier", false);
        this.locks = new ObjectLock<TitanGuid>();
    }

    /**
     * Get the {@link Dossier.Entry} for the given {@link NodeAddress}.
     * If the address does not have an entry, one is created and returned.
     *
     * @param address the address for which a {@code Dossier.Entry} is desired
     *
     * @return the address's {@code Dossier.Entry}
     */
    public Dossier.Entry getEntryAndLock(NodeAddress address) throws ClassCastException, SecurityException {
        TitanGuid objectId = address.getObjectId();

        this.locks.lock(objectId);
        try {
            Dossier.Entry entry = this.entries.get(objectId);
            if (entry == null) {
                Dossier.Entry e = new Dossier.Entry(address);
                this.entries.put(objectId, e);
                entry = this.entries.get(objectId);
                if (entry == null) {
                    System.out.printf("unable to put and get %s: file %s%n", objectId, this.entries.makePath(objectId));
                }
            }

            assert(entry != null);
            return entry;
        } catch (AssertionError e) {
            this.locks.unlock(objectId);
            throw e;
        } catch (ClassCastException e) {
            this.locks.unlock(objectId);
            throw e;
        } catch (SecurityException e) {
            this.locks.unlock(objectId);
            throw e;
        } catch (Exception e) {
        	e.printStackTrace();
        	throw new RuntimeException(e);
        }
    }

    public void put(Dossier.Entry e) {
        this.locks.assertLock(e.address.getObjectId());
        this.entries.put(e.address.getObjectId(), e);
    }

    public void removeEntry(Dossier.Entry e) {
        this.locks.assertLock(e.address.getObjectId());
        this.entries.remove(e.address.getObjectId());
    }

    public boolean unlockEntry(Dossier.Entry e) {
        this.locks.assertLock(e.address.getObjectId());
        return this.locks.unlock(e.address.getObjectId());
    }

    protected static class BeehiveObjectIdFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return TitanGuidImpl.IsValid(name);
        }
    }

    public void failure(NodeAddress address, String virtue) {
        Dossier.Entry e = this.getEntryAndLock(address);
        try {
            e.getProbability(virtue).failure();
            this.entries.put(address.getObjectId(), e);
        } finally {
            this.unlockEntry(e);
        }
    }

    public long getTimestamp(NodeAddress address) {
        Dossier.Entry e = this.getEntryAndLock(address);
        try {
            return e.timestamp;
        } finally {
            this.unlockEntry(e);
        }
    }

    public Set<Map.Entry<TitanGuid,Dossier.Entry>> entrySet() {
        return this.entries.entrySet();
    }

    public Set<TitanGuid> keySet() {
        return this.entries.keySet();
    }
}
