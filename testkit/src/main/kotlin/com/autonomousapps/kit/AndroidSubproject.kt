package com.autonomousapps.kit

class AndroidSubproject(
  name: String,
  variant: String,
  buildScript: BuildScript,
  sources: List<Source>,
  files: List<File> = emptyList(),
  val manifest: AndroidManifest = AndroidManifest.DEFAULT_APP,
  val styles: AndroidStyleRes = AndroidStyleRes.DEFAULT,
  val strings: AndroidStringRes = AndroidStringRes.DEFAULT,
  val colors: AndroidColorRes = AndroidColorRes.DEFAULT,
  val layouts: List<AndroidLayout> = emptyList()
) : Subproject(
  name = name,
  buildScript = buildScript,
  sources = sources,
  files = files,
  variant = variant
) {

  class Builder {
    var name: String? = null
    var variant: String = "debug"
    var buildScript: BuildScript = BuildScript()
    var sources: List<Source> = emptyList()
    var manifest: AndroidManifest = AndroidManifest.DEFAULT_APP
    var styles: AndroidStyleRes = AndroidStyleRes.DEFAULT
    var strings: AndroidStringRes = AndroidStringRes.DEFAULT
    var colors: AndroidColorRes = AndroidColorRes.DEFAULT
    var layouts: List<AndroidLayout> = emptyList()
    val files: MutableList<File> = mutableListOf()

    // sub-builders
    private var buildScriptBuilder: BuildScript.Builder? = null

    fun withBuildScript(block: BuildScript.Builder.() -> Unit) {
      val builder = buildScriptBuilder ?: defaultBuildScriptBuilder()
      buildScript = with(builder) {
        block(this)
        // store for later building-upon
        buildScriptBuilder = this
        build()
      }
    }

    private fun defaultBuildScriptBuilder(): BuildScript.Builder {
      return BuildScript.Builder().apply {
        plugins = mutableListOf(Plugin.androidAppPlugin)
        repositories = Repository.DEFAULT
        android = AndroidBlock.defaultAndroidAppBlock(false)
        dependencies = emptyList()
        additions = ""
      }
    }

    fun withFile(path: String, content: String) {
      withFile(File(path, content))
    }

    fun withFile(file: File) {
      files.add(file)
    }

    fun build(): AndroidSubproject {
      val name = name ?: error("'name' must not be null")
      return AndroidSubproject(
        name = name,
        variant = variant,
        buildScript = buildScript,
        sources = sources,
        manifest = manifest,
        styles = styles,
        strings = strings,
        colors = colors,
        layouts = layouts,
        files = files
      )
    }
  }
}
