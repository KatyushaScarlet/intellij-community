/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.test;

import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.WithMutedInDatabaseRunTest;

import static com.intellij.testFramework.RunAll.runAll;
import static org.jetbrains.kotlin.test.MuteWithDatabaseKt.isIgnoredInDatabaseWithLog;

/**
 * Please use KotlinLightCodeInsightFixtureTestCase as the base class for all new tests.
 */
@WithMutedInDatabaseRunTest
@Deprecated
public abstract class KotlinCodeInsightTestCase extends CodeInsightTestCase {
    private Ref<Disposable> vfsDisposable;

    @Override
    protected void setUp() throws Exception {
        vfsDisposable = KotlinTestUtils.allowProjectRootAccess(this);
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        runAll(
                () -> super.tearDown(),
                () -> KotlinTestUtils.disposeVfsRootAccess(vfsDisposable)
        );
    }

    @Override
    protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
        KotlinTestUtils.runTestWithThrowable(this, () -> super.runTestRunnable(testRunnable));
    }
}
