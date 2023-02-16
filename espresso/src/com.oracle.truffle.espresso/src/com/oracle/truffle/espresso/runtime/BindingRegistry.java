package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.classfile.attributes.CallinBindingsAttribute;
import com.oracle.truffle.espresso.descriptors.Symbol;

import java.util.WeakHashMap;

// TODO Lars: We do not use a binding registry per Truffle lang. This kills interop
public final class BindingRegistry {

    private static final WeakHashMap<Symbol<Symbol.Name>, CallinBindingsAttribute.MultiBinding> registeredBindings = new WeakHashMap<>();

    public BindingRegistry() {
    }

    public static void registerBinding(Symbol<Symbol.Name> name, CallinBindingsAttribute.MultiBinding multiBinding) {
        registeredBindings.put(name, multiBinding);
    }

    public static CallinBindingsAttribute.MultiBinding getBinding(Symbol<Symbol.Name> methodName) {
        return registeredBindings.get(methodName);
    }
}
