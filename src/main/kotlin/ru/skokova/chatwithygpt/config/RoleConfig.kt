package ru.skokova.chatwithygpt.config

data class RoleConfig(
    val roleId: String?
) {
    companion object {
        fun load(): RoleConfig {
            // Сначала проверяем переменную окружения
            val roleId = System.getenv("YANDEX_GPT_ROLE")
            return RoleConfig(roleId = roleId)
        }
    }
}