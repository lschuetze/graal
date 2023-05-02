/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;

import static com.oracle.truffle.espresso.classfile.attributes.CallinBindingsAttribute.*;
import static com.oracle.truffle.espresso.descriptors.Symbol.*;

final class Utils {
    /**
     * Creates a {@link DirectCallNode} that may {@link DirectCallNode#forceInlining() force-inline}
     * the callee, depending on the {@code ForceInline} annotation.
     */
    static DirectCallNode createAndMaybeForceInline(Method.MethodVersion resolvedMethod) {
        DirectCallNode callNode = DirectCallNode.create(resolvedMethod.getCallTarget());
        if (resolvedMethod.getMethod().isForceInline()) {
            callNode.forceInlining();
        }
        return callNode;
    }

    static MultiBinding getBindingForId(MultiBinding[] bindings, int callinId) {
        for (MultiBinding mb : bindings) {
            for (MultiBinding.BindingInfo info : mb.getBindingInfo()) {
                if (info.getCallinId() == callinId) {
                    return mb;
                }
            }
        }
        // Should not reach here
        CompilerDirectives.shouldNotReachHere();
        return null;
    }

    // can one do that over the iTableLookup ?
    static Method lookupLiftMethod(ObjectKlass teamKlass, int idx) {
        final String methodName = "_OT$liftTo$" + teamKlass.getConstantPool().symbolAt(idx).toString();
        for (Method method : teamKlass.getDeclaredMethods()) {
            if (method.getNameAsString().equals(methodName)) {
                return method;
            }
        }
        // should not reach here
        CompilerDirectives.shouldNotReachHere();
        return null;
    }

    static Method lookupRoleMethod(ObjectKlass teamKlass, ObjectKlass roleKlass, MultiBinding binding) {
        // TODO Lars: Implement a way to use the vtable
        // target = receiverKlass.vtableLookup(vtableIndex).getMethodVersion();
        final Symbol<Name> methodName = teamKlass.getConstantPool().symbolAt(binding.getRoleSelectorIndex());
        final Symbol<Signature> signature = teamKlass.getConstantPool().symbolAt(binding.getRoleSignatureIndex());
        return roleKlass.lookupMethod(methodName, signature);
    }
}
