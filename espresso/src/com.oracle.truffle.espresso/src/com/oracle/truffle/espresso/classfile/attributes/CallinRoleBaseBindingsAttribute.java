package com.oracle.truffle.espresso.classfile.attributes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class CallinRoleBaseBindingsAttribute extends Attribute {

    public static final Symbol<Name> NAME = Name.RoleBaseBindings;

    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    private final BindingInfo[] components;

    public static class BindingInfo {
        final int roleClassIndex;
        final int baseClassIndex;

        public BindingInfo(int roleClassIndex, int baseClassIndex) {
            this.roleClassIndex = roleClassIndex;
            this.baseClassIndex = baseClassIndex;
        }
    }

    public CallinRoleBaseBindingsAttribute(Symbol<Name> name, BindingInfo[] components) {
        super(name, null);
        this.components = components;
    }
}
