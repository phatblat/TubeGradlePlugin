package at.phatbl.tube

import org.gradle.api.Plugin
import org.gradle.api.Project

class TubePlugin: Plugin<Project> {
    lateinit var project: Project
    override fun apply(nullableProject: Project?) {
        project = nullableProject ?: return
        project.tasks.create("codeQuality")
    }
}
