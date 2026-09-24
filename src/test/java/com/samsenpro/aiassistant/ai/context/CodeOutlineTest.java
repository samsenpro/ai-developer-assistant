package com.samsenpro.aiassistant.ai.context;

import com.samsenpro.aiassistant.project.ProgrammingLanguage;
import org.junit.jupiter.api.Test;

import static com.samsenpro.aiassistant.support.TestFiles.USER_SERVICE;
import static org.assertj.core.api.Assertions.assertThat;

class CodeOutlineTest {

    private final CodeOutline outline = new CodeOutline();

    @Test
    void keepsDeclarationsAndSignaturesButNotBodies() {
        var lines = outline.outline(ProgrammingLanguage.JAVA, USER_SERVICE);

        assertThat(lines).extracting(CodeOutline.Line::text)
                .anyMatch(text -> text.contains("public class UserService"))
                .anyMatch(text -> text.contains("public User find(Long id)"))
                .anyMatch(text -> text.contains("import org.springframework"))
                .noneMatch(text -> text.contains("orElseThrow"));
        assertThat(lines).extracting(CodeOutline.Line::number).isSorted();
    }

    @Test
    void supportsPythonAndTypeScript() {
        var python = outline.outline(ProgrammingLanguage.PYTHON, """
                import os

                class Repo:
                    def find(self, id):
                        return None
                """);
        var typescript = outline.outline(ProgrammingLanguage.TYPESCRIPT, """
                import { Injectable } from '@angular/core';
                export class UserService {
                  find(id: number): User {
                    return this.cache[id];
                  }
                }
                export const toDto = (user: User) => ({ id: user.id });
                """);

        assertThat(python).extracting(CodeOutline.Line::number).containsExactly(1, 3, 4);
        assertThat(typescript).extracting(CodeOutline.Line::number).contains(1, 2, 3, 7).doesNotContain(4);
    }
}
