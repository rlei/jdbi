/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.generator;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.MethodSpec.Builder;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.extension.ExtensionMethod;
import org.jdbi.v3.core.extension.HandleSupplier;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.sqlobject.Handler;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.UncheckedHandler;
import org.jdbi.v3.sqlobject.internal.GeneratedSqlObjectInitData;

@SupportedAnnotationTypes("org.jdbi.v3.sqlobject.GenerateSqlObject")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class GenerateSqlObjectProcessor extends AbstractProcessor {
    private static final Set<ElementKind> ACCEPTABLE = EnumSet.of(ElementKind.CLASS, ElementKind.INTERFACE);
    private long counter = 0;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }
        final TypeElement gens = annotations.iterator().next();
        final Set<? extends Element> annoTypes = roundEnv.getElementsAnnotatedWith(gens);
        annoTypes.forEach(this::generate);
        return true;
    }

    private void generate(Element e) {
        try {
            generate0(e);
        } catch (Exception ex) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Failure: " + ex, e);
            throw new RuntimeException(ex);
        }
    }

    private void generate0(Element e) throws IOException {
        processingEnv.getMessager().printMessage(Kind.NOTE, String.format("[jdbi] generating for %s", e));
        if (!ACCEPTABLE.contains(e.getKind())) {
            throw new IllegalStateException("Generate on non-class: " + e);
        }
        if (!e.getModifiers().contains(Modifier.ABSTRACT)) {
            throw new IllegalStateException("Generate on non-abstract class: " + e);
        }

        final TypeElement te = (TypeElement) e;
        final String implName = te.getSimpleName().toString() + "Impl";
        final TypeSpec.Builder builder = TypeSpec.classBuilder(implName).addModifiers(Modifier.PUBLIC);
        final TypeName superName = TypeName.get(te.asType());
        if (te.getKind() == ElementKind.CLASS) {
            builder.superclass(superName);
        } else {
            builder.addSuperinterface(superName);
        }
        builder.addSuperinterface(SqlObject.class);

        final CodeBlock.Builder staticInit = CodeBlock.builder()
                .add("final $T handlerFactory = $T.initializer();\n",
                        new GenericType<Function<Method, Handler>>() {}.getType(),
                        GeneratedSqlObjectInitData.class);
        final CodeBlock.Builder constructor = CodeBlock.builder()
                .add("this.handle = handle;\n")
                .add("config = handle.getConfig().createCopy();\n");

        final Set<String> configurers = new HashSet<>();
        final Set<DeclaredType> seen = new HashSet<>();
        final Queue<DeclaredType> types = new LinkedList<>();
        types.add((DeclaredType) e.asType());
        DeclaredType type;
        while ((type = types.poll()) != null) {
            if (!seen.add(type)) {
                continue;
            }
            final TypeElement typeElement = (TypeElement) type.asElement();
            Optional.of(typeElement.getSuperclass())
                .filter(e -> !(e instanceof NoType))
                .map(DeclaredType.class::cast)
                .ifPresent(types::add);
            typeElement.getInterfaces()
                .stream()
                .map(DeclaredType.class::cast)
                .forEach(types::add);
            processingEnv.getMessager().printMessage(Kind.WARNING, "type " + type);
            type.getAnnotationMirrors().forEach(am -> am.);
        }

        builder.addMethod(generateMethod(builder, staticInit, element(SqlObject.class, "getHandle")));
        builder.addMethod(generateMethod(builder, staticInit, element(SqlObject.class, "withHandle")));
        builder.addField(HandleSupplier.class, "handle", Modifier.PRIVATE, Modifier.FINAL);
        builder.addField(ConfigRegistry.class, "config", Modifier.PRIVATE, Modifier.FINAL);

        te.getEnclosedElements().stream()
                .filter(ee -> ee.getKind() == ElementKind.METHOD)
                .filter(ee -> ee.getModifiers().contains(Modifier.ABSTRACT))
                .map(ee -> generateMethod(builder, staticInit, ee))
                .forEach(builder::addMethod);

        builder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(HandleSupplier.class, "handle")
                .addCode(constructor.build())
                .build());

        builder.addStaticBlock(staticInit.build());

        final JavaFileObject file = processingEnv.getFiler().createSourceFile(processingEnv.getElementUtils().getPackageOf(e) + "." + implName, e);
        try (Writer out = file.openWriter()) {
            JavaFile.builder(packageName(te), builder.build()).build().writeTo(out);
        }
    }

    private MethodSpec generateMethod(TypeSpec.Builder typeBuilder, CodeBlock.Builder staticInit, Element e) {
        final Types typeUtils = processingEnv.getTypeUtils();
        final ExecutableElement ee = (ExecutableElement) e;
        final Builder builder = MethodSpec.overriding(ee);
        final String paramNames = ee.getParameters().stream()
                .map(VariableElement::getSimpleName)
                .map(Object::toString)
                .collect(Collectors.joining(","));
        final String paramTypes = ee.getParameters().stream()
                .map(VariableElement::asType)
                .map(typeUtils::erasure)
                .map(t -> t + ".class")
                .collect(Collectors.joining(","));
        final String methodField = "m_" + e.getSimpleName() + "_" + counter;
        final String handlerField = "h_" + e.getSimpleName() + "_" + counter++;
        typeBuilder.addField(ExtensionMethod.class, methodField, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
        typeBuilder.addField(UncheckedHandler.class, handlerField, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
        staticInit.add("$L = $T.lookupMethod($S, new Class<?>[] {$L});\n",
                methodField,
                GeneratedSqlObjectInitData.class,
                e.getSimpleName(),
                paramTypes);
        staticInit.add("$L = UncheckedHandler.of(handlerFactory.apply($L.getMethod()));\n",
                handlerField,
                methodField);

        final CodeBlock body = CodeBlock.builder()
                .add("$L handle.invokeInContext($L, config, () -> $L.invoke(this, new Object[] {$L}, handle));\n",
                    ee.getReturnType().getKind() == TypeKind.VOID ? "" : ("return (" + ee.getReturnType().toString() + ")"), // NOPMD
                    methodField,
                    handlerField,
                    paramNames)
                .build();

        return builder.addCode(body).build();
    }

    private Element element(Class<?> klass, String name) {
        return processingEnv.getElementUtils().getTypeElement(klass.getName()).getEnclosedElements()
                .stream()
                .filter(e -> e.getSimpleName().toString().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no " + klass + "." + name + " found"));
    }

    private String packageName(Element e) {
        return processingEnv.getElementUtils().getPackageOf(e).toString();
    }
}
