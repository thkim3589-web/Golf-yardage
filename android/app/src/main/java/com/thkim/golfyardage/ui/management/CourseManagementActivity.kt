package com.thkim.golfyardage.ui.management

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.thkim.golfyardage.R
import com.thkim.golfyardage.data.CourseRepository
import com.thkim.golfyardage.data.HoleStrategyStore
import com.thkim.golfyardage.databinding.ActivityCourseManagementBinding

class CourseManagementActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_COURSE_ID = "extra_course_id"
        private const val EXTRA_HOLE_NUMBER = "extra_hole_number"

        fun start(context: Context, courseId: String, holeNumber: Int) {
            context.startActivity(
                Intent(context, CourseManagementActivity::class.java)
                    .putExtra(EXTRA_COURSE_ID, courseId)
                    .putExtra(EXTRA_HOLE_NUMBER, holeNumber)
            )
        }
    }

    private lateinit var binding: ActivityCourseManagementBinding
    private lateinit var courseId: String
    private var holeNumber: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCourseManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra(EXTRA_COURSE_ID)
        if (id == null) {
            finish()
            return
        }
        courseId = id
        holeNumber = intent.getIntExtra(EXTRA_HOLE_NUMBER, 1)

        val course = CourseRepository.loadCourse(this, courseId)
        binding.toolbar.title = course.name
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.textHoleTitle.text = getString(R.string.management_hole_title_format, holeNumber)

        binding.canvas.setDrawing(HoleStrategyStore.load(this, courseId, holeNumber))

        binding.btnToolDraw.setOnClickListener { selectTool(StrategyTool.DRAW) }
        binding.btnToolHazard.setOnClickListener { selectTool(StrategyTool.HAZARD) }
        binding.btnClear.setOnClickListener { binding.canvas.clearAll() }

        selectTool(StrategyTool.DRAW)
    }

    override fun onPause() {
        super.onPause()
        HoleStrategyStore.save(this, courseId, holeNumber, binding.canvas.getDrawing())
    }

    private fun selectTool(tool: StrategyTool) {
        binding.canvas.tool = tool
        val activeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_orange))
        val inactiveColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.explore_surface))
        binding.btnToolDraw.backgroundTintList = if (tool == StrategyTool.DRAW) activeColor else inactiveColor
        binding.btnToolHazard.backgroundTintList = if (tool == StrategyTool.HAZARD) activeColor else inactiveColor
        binding.textGuide.text = if (tool == StrategyTool.DRAW) {
            getString(R.string.management_guide_draw)
        } else {
            getString(R.string.management_guide_hazard)
        }
    }
}
