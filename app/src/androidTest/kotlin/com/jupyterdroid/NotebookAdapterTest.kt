package com.jupyterdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupyterdroid.kernel.ExecutionResult
import com.jupyterdroid.model.Cell
import com.jupyterdroid.ui.NotebookAdapter
import io.noties.markwon.Markwon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotebookAdapterTest {

    private fun adapterFor(cells: MutableList<Cell>) = NotebookAdapter(
        cells,
        onRunCell = {},
        markwon = Markwon.create(InstrumentationRegistry.getInstrumentation().targetContext),
        onDeleteRequested = {},
        onStartDrag = {}
    )

    @Test
    fun moveCellReordersList() {
        val cells = mutableListOf<Cell>(Cell.Code(source = "a"), Cell.Code(source = "b"), Cell.Code(source = "c"))
        adapterFor(cells).moveCell(0, 2)
        assertEquals(listOf("b", "c", "a"), cells.map { (it as Cell.Code).source })
    }

    @Test
    fun moveCellOutOfBoundsIsNoOp() {
        val cells = mutableListOf<Cell>(Cell.Code(source = "a"))
        val adapter = adapterFor(cells)
        adapter.moveCell(0, 1)
        adapter.moveCell(-1, 0)
        adapter.moveCell(0, 0)
        assertEquals(1, cells.size)
        assertEquals("a", (cells[0] as Cell.Code).source)
    }

    @Test
    fun deleteReturnsCellAndRestoreReinsertsIt() {
        val cells = mutableListOf<Cell>(Cell.Code(source = "a"), Cell.Markdown(source = "b"))
        val adapter = adapterFor(cells)
        val removed = adapter.deleteCell(0)
        assertEquals(1, cells.size)
        assertEquals("a", (removed as Cell.Code).source)
        adapter.restoreCell(0, removed)
        assertEquals(2, cells.size)
        assertSame(removed, cells[0])
    }

    @Test
    fun updateCellOutputTargetsCellByIdentityNotEquality() {
        // Two cells with EQUAL content — identity must disambiguate.
        val first = Cell.Code(source = "x")
        val second = Cell.Code(source = "x")
        val cells = mutableListOf<Cell>(first, second)
        val adapter = adapterFor(cells)

        adapter.updateCellOutput(second, ExecutionResult("out", "", 1))

        assertEquals("", (cells[0] as Cell.Code).output)   // first untouched
        assertEquals("out", (cells[1] as Cell.Code).output)
    }

    @Test
    fun updateCellOutputForDeletedCellIsNoOp() {
        val cell = Cell.Code(source = "x")
        val cells = mutableListOf<Cell>(cell)
        val adapter = adapterFor(cells)
        adapter.deleteCell(0)
        adapter.updateCellOutput(cell, ExecutionResult("out", "", 1)) // must not throw
        assertEquals(0, cells.size)
    }

    @Test
    fun restoreCellClampsOutOfRangePosition() {
        val cells = mutableListOf<Cell>(Cell.Code(source = "a"))
        val adapter = adapterFor(cells)
        val removed = adapter.deleteCell(0)
        adapter.restoreCell(5, removed) // stale position past list end — must not throw
        assertEquals(1, cells.size)
        assertSame(removed, cells[0])
    }
}
