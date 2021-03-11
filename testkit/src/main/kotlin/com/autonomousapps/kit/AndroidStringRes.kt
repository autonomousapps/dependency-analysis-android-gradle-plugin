package com.autonomousapps.kit

class AndroidStringRes(val content: String) {
  override fun toString(): String = content

  companion object {
    val DEFAULT = AndroidStringRes(
      """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
          <string name ="hello_world">Hello, world</string>
        </resources>
      """.trimIndent()
    )
  }
}