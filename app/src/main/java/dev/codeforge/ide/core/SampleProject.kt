package dev.codeforge.ide.core

import java.io.File

/** Small starter project so a first run has something real to open, run and compile. */
object SampleProject {
    fun create(dir: File) {
        dir.mkdirs()
        File(dir, "README.md").writeText(
            """
            |# hello-codeforge
            |
            |Open a file and tap **Run**. The first run of each language asks you to install
            |its toolchain from the **Tools** tab (an internet connection is needed once).
            |
            |- `hello.py` - Python 3
            |- `hello.js` - Node.js
            |- `hello.c` - C (GCC)
            |- `hello.cpp` - C++ (G++)
            |- `Hello.java` - Java (OpenJDK)
            |- `hello.sh` - shell script (works without installing anything)
            |
            """.trimMargin(),
        )
        File(dir, "hello.py").writeText(
            "name = input(\"What's your name? \")\nprint(f\"Hello, {name}! This ran on Python inside CodeForge.\")\n",
        )
        File(dir, "hello.js").writeText(
            "const os = require('os');\nconsole.log(`Hello from Node.js \${process.version} on \${os.arch()}`);\n",
        )
        File(dir, "hello.c").writeText(
            "#include <stdio.h>\n\nint main(void) {\n    for (int i = 1; i <= 3; i++) {\n        printf(\"Hello from C, line %d\\n\", i);\n    }\n    return 0;\n}\n",
        )
        File(dir, "hello.cpp").writeText(
            "#include <iostream>\n#include <vector>\n\nint main() {\n    std::vector<int> v{3, 1, 2};\n    int sum = 0;\n    for (int x : v) sum += x;\n    std::cout << \"Hello from C++, sum = \" << sum << std::endl;\n    return 0;\n}\n",
        )
        File(dir, "Hello.java").writeText(
            "public class Hello {\n    public static void main(String[] args) {\n        System.out.println(\"Hello from Java \" + System.getProperty(\"java.version\"));\n    }\n}\n",
        )
        File(dir, "hello.sh").writeText(
            "#!/bin/sh\necho \"Hello from $(uname -s) $(uname -m)\"\necho \"Working directory: $(pwd)\"\nls -la\n",
        )
    }
}
