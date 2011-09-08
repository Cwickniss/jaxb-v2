/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.tools.xjc.reader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import com.sun.tools.xjc.ErrorReceiver;
import com.sun.tools.xjc.model.Model;

/**
 * Holds all the binding related singleton components in a "ring",
 * and let you access those components, creating them as necessary.
 *
 * <p>
 * A {@link Ring} is local to a thread,
 * and only one instanceof {@link Ring} can be active at any given time.
 *
 * Use {@link #begin()} and {@link #end(Ring)} to start/end a ring scope.
 * Inside a scope, use {@link #get()} to obtain the instance.
 *
 * <p>
 * When a {@link Model} is built by the reader, an active {@link Ring} scope
 * is assumed.
 *
 *
 * <h2>Components in Ring</h2>
 * <p>
 * Depending on the schema language we are dealing with, different
 * components are in the model. But at least the following components
 * are in the ring.
 *
 * <ul>
 *  <li>{@link ErrorReceiver}
 * </ul>
 *
 * @author Kohsuke Kawaguchi
 */
public final class Ring {

    private final Map<Class,Object> components = new HashMap<Class,Object>();

    private static final ThreadLocal<Ring> instances = new ThreadLocal<Ring>() {
        @Override
        public Ring initialValue() {
            return new Ring();
        }
    };

    private Ring() {}

    public static <T> void add( Class<T> clazz, T instance ) {
        assert !get().components.containsKey(clazz);
        get().components.put(clazz,instance);
    }

    public static <T> void add( T o ) {
        add((Class<T>)o.getClass(),o);
    }

    public static <T> T get( Class<T> key ) {
        T t = (T)get().components.get(key);
        if(t==null) {
            try {
                Constructor<T> c = key.getDeclaredConstructor();
                c.setAccessible(true);
                t = c.newInstance();
                if(!get().components.containsKey(key))
                    // many components register themselves.
                    add(key,t);
            } catch (InstantiationException e) {
                throw new Error(e);
            } catch (IllegalAccessException e) {
                throw new Error(e);
            } catch (NoSuchMethodException e) {
                throw new Error(e);
            } catch (InvocationTargetException e) {
                throw new Error(e);
            }
        }

        assert t!=null;
        return t;
    }

    /**
     * A {@link Ring} instance is associated with a thread.
     */
    private static Ring get() {
        return instances.get();
    }

    /**
     * Starts a new scope.
     */
    public static Ring begin() {
        Ring r = null;
        synchronized (instances) {
            r = instances.get();
            instances.set(new Ring());
        }
        return r;
    }

    /**
     * Ends a scope.
     */
    public static void end(Ring old) {
        synchronized (instances) {
            instances.remove();
            instances.set(old);
        }
    }
}
