package com.realm.annotation.compiler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.realm.annotation.api.Entity;
import com.realm.annotation.api.Transient;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

public class Processor extends AbstractProcessor {
    private ErrorReporter errorReporter;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        errorReporter = new ErrorReporter(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        Collection<? extends Element> annotatedElements =
                roundEnvironment.getElementsAnnotatedWith(Entity.class);
        List<TypeElement> types = new ImmutableList.Builder<TypeElement>()
                .addAll(ElementFilter.typesIn(annotatedElements))
                .build();

        for (TypeElement type : types) {
            processType(type);
        }

        return true;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> supportedAnnotationTypes = new HashSet<>();
        supportedAnnotationTypes.add(Entity.class.getCanonicalName());
        supportedAnnotationTypes.add(Transient.class.getCanonicalName());
        return supportedAnnotationTypes;
    }

    private void processType(TypeElement type) {
        String fqClassName = generatedSubclassName(type);
        String className = TypeUtil.simpleNameOf(fqClassName);
        String source = generateClass(type, className);
        writeSourceFile(fqClassName, source, type);
    }

    private String generatedSubclassName(TypeElement type) {
        return generatedClassName(type, "Realm");
    }

    private String generatedClassName(TypeElement type, String prefix) {
        String name = type.getSimpleName().toString();
        while (type.getEnclosingElement() instanceof TypeElement) {
            type = (TypeElement) type.getEnclosingElement();
            name = type.getSimpleName() + "_" + name;
        }
        String pkg = TypeUtil.packageNameOf(type);
        String dot = pkg.isEmpty() ? "" : ".";
        return pkg + dot + prefix + name;
    }

    private String generateClass(TypeElement type, String className) {
        List<VariableElement> validFields = getValidFieldsOrError(type);
        if (validFields.isEmpty()) {
            errorReporter.abortWithError("generateClass error, all fields are declared PRIVATE or STATIC", type);
        }

        String pkg = TypeUtil.packageNameOf(type);
        TypeSpec.Builder subClass = TypeSpec.classBuilder(className)
                .addModifiers(PUBLIC)
                .superclass(ClassName.get("io.realm", "RealmObject"))
                .addMethod(MethodSpec.constructorBuilder().addModifiers(PUBLIC).build())
                .addMethod(generateConstructor(validFields));

        String primaryKey = type.getAnnotation(Entity.class).primaryKey();

        List<String> ignores = Arrays.asList(type.getAnnotation(Entity.class).ignores());

        for (VariableElement field : validFields) {
            final FieldSpec.Builder builder = FieldSpec.builder(TypeName.get(field.asType()), field.getSimpleName().toString(), PUBLIC);
            final String name = field.getSimpleName().toString();
            if (primaryKey.equals(name)) {
                builder.addAnnotation(ClassName.get("io.realm.annotations", "PrimaryKey"));
            } else if (ignores.contains(name)) {
                builder.addAnnotation(ClassName.get("io.realm.annotations", "Ignore"));
            }
            subClass.addField(
                    builder.build()
            );
        }

        JavaFile javaFile = JavaFile.builder(pkg, subClass.build()).build();
        return javaFile.toString();
    }

    private MethodSpec generateConstructor(List<VariableElement> properties) {
        List<ParameterSpec> params = Lists.newArrayListWithCapacity(properties.size());
        for (VariableElement field : properties) {
            params.add(ParameterSpec.builder(TypeName.get(field.asType()), field.getSimpleName().toString()).build());
        }

        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addParameters(params);

        for (ParameterSpec param : params) {
            builder.addStatement("this.$N = $N", param.name, param.name);
        }

        builder.addModifiers(PUBLIC);

        return builder.build();
    }

    private List<VariableElement> getValidFieldsOrError(TypeElement type) {
        List<VariableElement> allFields = ElementFilter.fieldsIn(type.getEnclosedElements());
        List<VariableElement> validFields = new ArrayList<>();

        for (VariableElement field : allFields) {
            if (!field.getModifiers().contains(STATIC) && field.getAnnotation(Transient.class) == null) {
                validFields.add(field);
            }
        }

        return validFields;
    }

    private void writeSourceFile(String className, String text, TypeElement originatingType) {
        try {
            JavaFileObject sourceFile =
                    processingEnv.getFiler().createSourceFile(className, originatingType);
            Writer writer = sourceFile.openWriter();
            try {
                writer.write(text);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            // This should really be an error, but we make it a warning in the hope of resisting Eclipse
            // bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599. If that bug manifests, we may get
            // invoked more than once for the same file, so ignoring the ability to overwrite it is the
            // right thing to do. If we are unable to write for some other reason, we should get a compile
            // error later because user code will have a reference to the code we were supposed to
            // generate (new AutoValue_Foo() or whatever) and that reference will be undefined.
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                    "Could not write generated class " + className + ": " + e);
        }
    }
}
