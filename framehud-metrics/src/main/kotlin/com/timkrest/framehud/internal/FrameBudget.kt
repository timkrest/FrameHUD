package com.timkrest.framehud.internal

internal fun overrunAgainst(budgetMs: Int?, totalMs: Float, displayOverrunMs: Float): Float =
    if (budgetMs == null) displayOverrunMs else totalMs - budgetMs
