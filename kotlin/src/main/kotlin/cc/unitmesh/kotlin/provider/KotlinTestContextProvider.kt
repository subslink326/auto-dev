package cc.unitmesh.kotlin.provider

import cc.unitmesh.devti.custom.test.TemplatedTestPrompt
import cc.unitmesh.devti.gui.chat.ChatActionType
import cc.unitmesh.devti.provider.context.ChatContextItem
import cc.unitmesh.devti.provider.context.ChatCreationContext
import cc.unitmesh.idea.provider.JavaTestContextProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction


class KotlinTestContextProvider : JavaTestContextProvider() {
    override fun langFileSuffix(): String = "kt"

    override fun isApplicable(project: Project, creationContext: ChatCreationContext): Boolean {
        return creationContext.action == ChatActionType.GENERATE_TEST && creationContext.sourceFile?.language is KotlinLanguage
    }

    override suspend fun collect(project: Project, creationContext: ChatCreationContext): List<ChatContextItem> {
        val fileName = creationContext.sourceFile?.name

        val isSpringRelated = checkIsSpringRelated(creationContext)

        var prompt = baseTestPrompt + junitRule(project)

        val language = creationContext.sourceFile?.language?.displayName ?: "Kotlin"

        val finalPrompt = when {
            isController(fileName) && isSpringRelated -> {
                var testControllerPrompt = prompt + "\n" + """
                            |- Use appropriate Spring test annotations such as `@MockBean`, `@Autowired`, `@WebMvcTest`, `@DataJpaTest`, `@AutoConfigureTestDatabase`, `@AutoConfigureMockMvc`, `@SpringBootTest` etc.
                            |""".trimMargin()

                val lookup = project.service<TemplatedTestPrompt>().lookup("ControllerTest.kt")
                if (lookup != null) {
                    testControllerPrompt += "Here is a template as example\n```$language\n$lookup\n```\n"
                }

                ChatContextItem(JavaTestContextProvider::class, testControllerPrompt)
            }

            isService(fileName) && isSpringRelated -> {
                var testServicePrompt = prompt + "\n" + """
                            |- Follow the common Spring code style by using the AssertJ library.
                            |- Assume that the database is empty before each test and create valid entities with consideration for data constraints (jakarta.validation.constraints).
                            |""".trimMargin()


                val lookup = project.service<TemplatedTestPrompt>().lookup("ServiceTest.kt")
                if (lookup != null) {
                    testServicePrompt += "Here is a template as example\n```$language\n$lookup\n```\n"
                }

                ChatContextItem(JavaTestContextProvider::class, testServicePrompt)
            }

            else -> {
                val lookup = project.service<TemplatedTestPrompt>().lookup("Test.java")
                if (lookup != null) {
                    prompt += "Here is a template as example\n```$language\n$lookup\n```\n"
                }

                ChatContextItem(JavaTestContextProvider::class, prompt)
            }
        }

        return listOf(finalPrompt)
    }


    override fun isSpringRelated(element: PsiElement): Boolean {
        val imports = (element.containingFile as KtFile).importList?.imports?.map { it.text } ?: emptyList()
        when (element) {
            is KtNamedFunction -> {
                val annotations: List<KtAnnotationEntry> = element.annotationEntries
                for (annotation in annotations) {
                    val refName = annotation.typeReference?.text ?: continue
                    imports.forEach { import ->
                        // in some case is import *, so we need to check the end of the import
                        if (refName.endsWith("Mapping") && import.contains("import org.springframework.web.bind.annotation.*")) {
                            return true
                        }

                        if (!import.endsWith(refName)) {
                            return@forEach
                        }

                        if (import.contains("org.springframework.web.bind")) {
                            return true
                        }
                    }
                }
            }

            is KtClassOrObject -> {
                val annotations = element.annotationEntries
                for (annotation in annotations) {
                    val refName = annotation.typeReference?.text ?: continue
                    imports.forEach { import ->
                        if (refName.endsWith("RestController") && import.contains("import org.springframework.web.bind.annotation.*")) {
                            return true
                        }

                        if (!import.endsWith(refName)) {
                            return@forEach
                        }

                        if (import.contains("org.springframework.web.bind")) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }
}
