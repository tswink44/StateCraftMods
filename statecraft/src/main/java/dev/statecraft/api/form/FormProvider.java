package dev.statecraft.api.form;

@FunctionalInterface
public interface FormProvider {
    void describe(FormContext context, FormBuilder form);
}
