package org.jbpm.formModeler.kie.services.form.provider;

import org.apache.commons.logging.Log;
import org.jbpm.formModeler.api.client.FormRenderContext;
import org.jbpm.formModeler.api.client.FormRenderContextManager;
import org.jbpm.formModeler.api.model.Form;
import org.jbpm.formModeler.core.config.FormSerializationManager;
import org.jbpm.kie.services.api.RuntimeDataService;
import org.jbpm.kie.services.impl.form.FormProvider;
import org.jbpm.kie.services.impl.model.ProcessDesc;
import org.kie.api.task.model.Task;

import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class FormModelerFormProvider implements FormProvider {
    @Inject
    protected Log log;

    @Inject
    private RuntimeDataService dataService;

    @Inject
    private FormSerializationManager formSerializationManager;

    @Inject
    private FormRenderContextManager formRenderContextManager;

    @Override
    public int getPriority() {
        return 2;
    }

    @Override
    public String render(String name, ProcessDesc process, Map<String, Object> renderContext) {
        InputStream template = null;
        if (process.getForms().containsKey(process.getId())) {
            template = new ByteArrayInputStream(process.getForms().get(process.getId()).getBytes());
        } else if (process.getForms().containsKey(process.getId() + "-taskform.form")) {
            template = new ByteArrayInputStream(process.getForms().get(process.getId() + "-taskform.form").getBytes());
        }

        if (template == null) return null;

        return renderProcessForm(process, template, renderContext);
    }

    @Override
    public String render(String name, Task task, ProcessDesc process, Map<String, Object> renderContext) {
        InputStream template = null;
        if(task != null && process != null){
            String taskName = task.getNames().get(0).getText();
            if (process.getForms().containsKey(taskName)) {
                template = new ByteArrayInputStream(process.getForms().get(taskName).getBytes());
            } else if (process.getForms().containsKey(taskName.replace(" ", "")+ "-taskform.form")) {
                template = new ByteArrayInputStream(process.getForms().get(taskName.replace(" ", "") + "-taskform.form").getBytes());
            }
        }

        if (template == null) return null;

        return renderTaskForm(task, template, renderContext);
    }

    protected String renderTaskForm(Task task, InputStream template, Map<String, Object> renderContext) {
        String result = null;
        try {
            Form form = formSerializationManager.loadFormFromXML(template);

            Map inputs = new HashMap();

            Map outputs = (Map) renderContext.get("outputs");

            Map m = (Map) renderContext.get("inputs");
            if (m != null) inputs.putAll(m);

            inputs.put("task", task);

            // Adding forms to context while forms are'nt available on marshaller classloader
            FormRenderContext context = formRenderContextManager.newContext(form, inputs, outputs, buildContextForms(task));
            context.setMarshaller(renderContext.get("marshallerContext"));

            String status = task.getTaskData().getStatus().name();
            boolean disabled = "Reserved".equals(status) || "Ready".equals(status);
            context.setDisabled(disabled);
            result = context.getUID();

        } catch (Exception e) {
            log.warn("Error rendering form: ", e);
        }

        return result;
    }

    protected String renderProcessForm(ProcessDesc process, InputStream template, Map<String, Object> renderContext) {
        String result = null;
        try {
            Form form = formSerializationManager.loadFormFromXML(template);

            Map ctx = new HashMap();

            ctx.put("process", process);

            // Adding forms to context while forms are'nt available on marshaller classloader
            FormRenderContext context = formRenderContextManager.newContext(form, ctx, new HashMap<String, Object>(), buildContextForms(process));
            context.setMarshaller(renderContext.get("marshallerContext"));

            result = context.getUID();
        } catch (Exception e) {
            log.warn("Error rendering form: ", e);
        }

        return result;
    }

    protected Map<String, Object> buildContextForms(Task task) {
        ProcessDesc processDesc = dataService.getProcessById(task.getTaskData().getProcessId());
        return buildContextForms(processDesc);
    }

    protected Map<String, Object> buildContextForms(ProcessDesc process) {
        Map<String, String> forms = process.getForms();

        Map<String, Object> ctxForms = new HashMap<String, Object>();


        for (Iterator it = forms.keySet().iterator(); it.hasNext();) {
            String key = (String) it.next();
            if (!key.endsWith(".form")) continue;
            String value = forms.get(key);
            ctxForms.put(key, value);
        }
        return ctxForms;
    }
}