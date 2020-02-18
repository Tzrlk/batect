/*
   Copyright 2017-2020 Charles Korn.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package batect.ioc

import batect.config.Task
import org.kodein.di.bindings.Scope
import org.kodein.di.bindings.ScopeRegistry
import org.kodein.di.bindings.StandardScopeRegistry
import java.util.WeakHashMap

object TaskScope : Scope<Task> {
    private val registries = WeakHashMap<Task, ScopeRegistry>()

    override fun getRegistry(context: Task): ScopeRegistry = registries.getOrPut(context) { StandardScopeRegistry() }

    fun close(context: Task) {
        registries.remove(context)?.close()
    }
}