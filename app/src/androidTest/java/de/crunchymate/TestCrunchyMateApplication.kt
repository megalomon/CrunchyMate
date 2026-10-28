package de.crunchymate

class TestCrunchyMateApplication : CrunchyMateApplication() {
    override fun createContainer(): AppContainer = AppContainer(this, TestStorage.DB_NAME, TestStorage.IMAGE_DIR)
}
