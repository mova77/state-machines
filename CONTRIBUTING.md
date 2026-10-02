# Contributing

Thank you for helping. Issues and pull requests are welcome.

- **Before a large change**, open an issue first, so that we can agree on the approach before you spend time on it.
- **Build:** `mvn clean verify` must pass on JDK 21. It compiles with `-Xlint:all -Werror`, runs the tests, and checks the javadoc with doclint.
- **Tests:** every behaviour change comes with a test (JUnit Jupiter and AssertJ).
- **Dependencies:** the library has no dependencies beyond the JDK outside test scope, and a pull request must not add any. The build enforcer and `scripts/check-no-runtime-deps.sh` check this.
- **Style:** follow the existing code and `.editorconfig`. Each new source file starts with the SPDX header used by the existing files.
- **Commits:** keep each pull request focused on one change, and write a commit message that explains why the change is made.

By contributing, you agree that your contributions are licensed under the MIT License of this project.
