/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.incremental;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.LabelNode;

import java.util.List;

public class MethodRedirection extends Redirection {

    public final Type type;

    MethodRedirection(LabelNode label, String name, Type type) {
        super(label, name);
        this.type = type;
    }

    /**
     * For methods, restore creates a return from the dispatch call, to exit the method
     * once the new implementation has been executed. for void methods, this is an empty return
     * and for all other methods, it returns the value from the dispatch call.
     * <p/>
     * Note that the generated bytecode does not have a direct translation to code, but as an
     * example, this restore implementation in combination with the base class generates the
     * following for methods with avoid return type:
     * <code>
     * if ($change != null) {
     *   $change.access$dispatch($name, new object[] { arg0, ... argsN })
     *   return;
     * }
     * $originalMethodBody
     *</code>
     * and the following for methods with a non-void return type:
     * <code>
     * if ($change != null) {
     *   return $change.access$dispatch($name, new object[] { arg0, ... argsN })
     * }
     * $originalMethodBody
     *</code>
     */
    @Override
    protected void restore(GeneratorAdapter mv, List<Type> args) {
        if (type == Type.VOID_TYPE) {
            mv.pop();
        } else {
            ByteCodeUtils.unbox(mv, type);
        }
        mv.returnValue();
    }
}