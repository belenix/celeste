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
package sunlabs.celeste;

import sunlabs.celeste.client.operation.CelesteOperation;
import sunlabs.titan.exception.TitanException;
import sunlabs.titan.node.AbstractTitanObject;
import sunlabs.titan.util.OrderedProperties;

public abstract class CelesteException extends TitanException {
    private final static long serialVersionUID = 1L;

    public static class AccessControlException extends CelesteException {
        private final static long serialVersionUID = 1L;

        public AccessControlException() {
            super();
        }

        public AccessControlException(String format, Object...args) {
            super(String.format(format, args));
        }

        public AccessControlException(String message) {
            super(message);
        }

        public AccessControlException(Throwable reason) {
            super(reason);
        }
        
        public AccessControlException(OrderedProperties metadata, String format, Object...args) {
            super(metadata, String.format(format, args));
        }

        public AccessControlException(OrderedProperties metadata, String message) {
            super(metadata, message);
        }

        /**
         * An AccessControlException that contains ancillary information in an {@link OrderedProperties} instance and a reason.
         * @param ancillary the ancillary information in an {@link OrderedProperties} instance.
         * @param reason a 
         */
        public AccessControlException(OrderedProperties ancillary, Throwable reason) {
            super(ancillary, reason);
        }
    }

    public static class AlreadyExistsException extends CelesteException {
        private final static long serialVersionUID = 1L;

        public AlreadyExistsException() {
            super();
        }

        public AlreadyExistsException(String format, Object...args) {
            super(String.format(format, args));
        }

        public AlreadyExistsException(String message) {
            super(message);
        }

        public AlreadyExistsException(Throwable reason) {
            super(reason);
        }
    }
    
    /**
     * This exception signals a problem with a credential.
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class CredentialException extends CelesteException {
        private final static long serialVersionUID = 1L;

        public CredentialException() {
            super();
        }

        public CredentialException(String format, Object...args) {
            super(String.format(format, args));
        }

        public CredentialException(String message) {
            super(message);
        }

        public CredentialException(Throwable reason) {
            super(reason);
        }
    }
    
    /**
     * Signal that a Credential could not be found.
     * 
     * @author Glenn Scott, Sun Microsystems Laboratories, Sun Microsystems, Inc.
     */
    public static class CredentialNotFoundException extends CelesteException {
        private final static long serialVersionUID = 1L;

        public CredentialNotFoundException() {
            super();
        }

        public CredentialNotFoundException(String format, Object...args) {
            super(String.format(format, args));
        }

        public CredentialNotFoundException(String message) {
            super(message);
        }

        public CredentialNotFoundException(Throwable reason) {
            super(reason);
        }
    }

    /**
     * Signals that a resource necessary for the completion of the operation was not found.
     */
    public static class NotFoundException extends CelesteException {
        private final static long serialVersionUID = 1L;

        public NotFoundException() {
            super();
        }

        public NotFoundException(String format, Object...args) {
            super(String.format(format, args));
        }

        public NotFoundException(String message) {
            super(message);
        }

        public NotFoundException(Throwable reason) {
            super(reason);
        }
    }
    
//    public static class PermissionDeniedException extends CelesteException {
//        private final static long serialVersionUID = 1L;
//
//        public PermissionDeniedException() {
//            super();
//        }
//
//        public PermissionDeniedException(String format, Object...args) {
//            super(String.format(format, args));
//        }
//
//        public PermissionDeniedException(String message) {
//            super(message);
//        }
//
//        public PermissionDeniedException(Throwable reason) {
//            super(reason);
//        }
//        
//        public PermissionDeniedException(BeehiveObject.Metadata metadata, String format, Object...args) {
//            super(metadata, String.format(format, args));
//        }
//
//        public PermissionDeniedException(BeehiveObject.Metadata metadata, String message) {
//            super(metadata, message);
//        }
//
//        public PermissionDeniedException(BeehiveObject.Metadata metadata, Throwable reason) {
//            super(metadata, reason);
//        }
//    }
    
    public static class DeletedException extends CelesteException {
        private final static long serialVersionUID = 1L;

        public DeletedException() {
            super();
        }

        public DeletedException(String format, Object...args) {
            super(String.format(format, args));
        }

        public DeletedException(String message) {
            super(message);
        }

        public DeletedException(Throwable reason) {
            super(reason);
        }
    }
    
    /**
     * Signal that the file is locked and the requested operation cannot be performed. 
     */
    public static class FileLocked extends CelesteException {
        private final static long serialVersionUID = 1L;

        public FileLocked() {
            super();
        }

        public FileLocked(String format, Object...args) {
            super(String.format(format, args));
        }

        public FileLocked(String message) {
            super(message);
        }

        public FileLocked(Throwable reason) {
            super(reason);
        }
        
        public FileLocked(OrderedProperties metadata, String format, Object...args) {
            super(metadata, String.format(format, args));
        }

        public FileLocked(OrderedProperties metadata, String message) {
            super(metadata, message);
        }

        public FileLocked(OrderedProperties metadata, Throwable reason) {
            super(metadata, reason);
        }
    }
    
    /**
     * Signal that the file is unlocked and the requested operation cannot be performed. 
     */
    public static class FileNotLocked extends CelesteException {
        private final static long serialVersionUID = 1L;

        public FileNotLocked() {
            super();
        }

        public FileNotLocked(String format, Object...args) {
            super(String.format(format, args));
        }

        public FileNotLocked(String message) {
            super(message);
        }

        public FileNotLocked(Throwable reason) {
            super(reason);
        }
        
        public FileNotLocked(OrderedProperties metadata, String format, Object...args) {
            super(metadata, String.format(format, args));
        }

        public FileNotLocked(OrderedProperties metadata, String message) {
            super(metadata, message);
        }

        public FileNotLocked(OrderedProperties metadata, Throwable reason) {
            super(metadata, reason);
        }
    }

    /**
     * Signals that a {@link CelesteOperation} parameter is either malformed, or is otherwise incorrect or incompatible with the operation being performed.
     */
    public static class IllegalParameterException extends CelesteException {
        private final static long serialVersionUID = 1L;

        public IllegalParameterException() {
            super();
        }

        public IllegalParameterException(String format, Object...args) {
            super(String.format(format, args));
        }

        public IllegalParameterException(String message) {
            super(message);
        }

        public IllegalParameterException(Throwable reason) {
            super(reason);
        }
    }
    
    /**
     * Signal that a signature was required and the supplied signature does not verify the associated data.
     * 
     */
    public static class VerificationException extends CelesteException {
        private final static long serialVersionUID = 1L;

        public VerificationException() {
            super();
        }

        public VerificationException(String format, Object...args) {
            super(String.format(format, args));
        }

        public VerificationException(String message) {
            super(message);
        }

        public VerificationException(Throwable reason) {
            super(reason);
        }
    }
    
    public static class OutOfDateException extends CelesteException {
        private final static long serialVersionUID = 1L;

        public OutOfDateException() {
            super();
        }

        public OutOfDateException(String format, Object...args) {
            super(String.format(format, args));
        }

        public OutOfDateException(String message) {
            super(message);
        }

        public OutOfDateException(Throwable reason) {
            super(reason);
        }
        
        public OutOfDateException(OrderedProperties metadata, String format, Object...args) {
            super(metadata, String.format(format, args));
        }

        public OutOfDateException(OrderedProperties metadata, String message) {
            super(metadata, message);
        }

        public OutOfDateException(OrderedProperties metadata, Throwable reason) {
            super(metadata, reason);
        }
        
        public String toString() {
            return String.format("%s: %s", super.toString(), this.getMetaData().toString());
        }
    }
    
    public static class RuntimeException extends CelesteException {
        private final static long serialVersionUID = 1L;

        public RuntimeException() {
            super();
        }

        public RuntimeException(String format, Object...args) {
            super(String.format(format, args));
        }

        public RuntimeException(String message) {
            super(message);
        }

        public RuntimeException(Throwable reason) {
            super(reason);
        }
    }
    
    /**
     * Signals that there was not enough storage space in the system to complete the operation.
     */
    public static class NoSpaceException extends CelesteException {
        private final static long serialVersionUID = 1L;

        public NoSpaceException() {
            super();
        }

        public NoSpaceException(String format, Object...args) {
            super(String.format(format, args));
        }

        public NoSpaceException(String message) {
            super(message);
        }

        public NoSpaceException(Throwable reason) {
            super(reason);
        }
    }
    

    private OrderedProperties metadata;
    
    public CelesteException() {
        super();
        this.metadata = new AbstractTitanObject.Metadata();
    }

    public CelesteException(String format, Object...args) {
        this(String.format(format, args));
    }

    public CelesteException(String message) {
        super(message);
        this.metadata = new AbstractTitanObject.Metadata();
    }

    public CelesteException(Throwable reason) {
        super(reason);
        this.metadata = new AbstractTitanObject.Metadata();
    }
    
    public CelesteException(OrderedProperties metadata) {
        this();
        this.metadata = metadata;
    }

    public CelesteException(OrderedProperties metadata, String format, Object...args) {
        this(String.format(format, args));
    }

    public CelesteException(OrderedProperties metadata, String message) {
        this(message);
        this.metadata = metadata;
    }

    public CelesteException(OrderedProperties metadata, Throwable reason) {
        this(reason);
        this.metadata = metadata;
    }
    
    public OrderedProperties getMetaData() {
        return this.metadata;
    }
    
    public void setMetaData(OrderedProperties metadata) {
        this.metadata = metadata;
    }
}
