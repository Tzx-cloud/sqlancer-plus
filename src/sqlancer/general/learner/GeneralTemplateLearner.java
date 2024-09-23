package sqlancer.general.learner;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import sqlancer.FeatureLearner;
import sqlancer.general.GeneralProvider.GeneralGlobalState;

public class GeneralTemplateLearner implements FeatureLearner {

    private final String chat_url = "https://api.openai.com/v1/chat/completions";
    private final String apiKey = System.getenv("OPENAI_API_KEY");

    private String raw_fragments = "";
    private GeneralGlobalState globalState;
    private String stmt_type;
    private String template;
    private String variables;
    private String system_prompt;

    // optional things
    private String examples = "";

    @Override
    public void learn() {
        String response = "";
        String url = "";

        // get the documentation url
        url = retrieveURL();
        response = getDialectFromURL(url);

        raw_fragments = process(response);
    }

    public GeneralTemplateLearner(GeneralGlobalState globalState, String stmt_type, String template, String variables,
            String system_prompt) {
        this.globalState = globalState;
        this.stmt_type = stmt_type;
        this.template = template;
        this.variables = variables;
        this.system_prompt = system_prompt;
    }

    @Override
    public void update() {
    }

    public String process(String response) {
        String content = response;

        // get rows with the placeholders and alternatives
        StringBuilder processed = new StringBuilder();
        String[] rows = content.split("\n");
        for (int i = 1; i < rows.length - 1; i++) {
            // validate if it's a placeholder&alternative row
            processed.append(rows[i]);
            processed.append("\n");
        }
        System.out.println(processed.toString());
        return processed.toString();
    }

    private String retrieveURL() {
        String doc_url = "";
        String model = "gpt-4o-mini";
        String system = "This GPT acts as a web crawler and assistant to help users find the correct URL or specific documentation related to Database Management Systems (DBMS). It should efficiently search the web and provide accurate, relevant URLs based on the user's query. The assistant will maintain a professional and formal tone, ensuring that users receive the most pertinent information. If the initial query is too broad or unclear, the assistant will ask for further clarification to narrow down the search. The responses should be concise, returning only the URL without any explanation.";
        String user = String.format("URL for %s %s",
                globalState.getDbmsSpecificOptions().getDatabaseEngineFactory().toString(), stmt_type);
        try {
            doc_url = getChatGPTResponse(model, system, user);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            doc_url = parseAndGetGPTContent(doc_url);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return doc_url;
    }

    private String getDialectFromURL(String url) {
        String response = "";
        String model = "gpt-4o";
        String system = system_prompt;
        String user = String.format("DBMS: %s\n" + //
                "Reference: %s\n" + //
                "Template: %s\n", globalState.getDbmsSpecificOptions().getDatabaseEngineFactory().toString(), url,
                template);
        if (variables != "") {
            user += "Available variables and their descriptions:\n" + variables;
        }
        if (examples != "") {
            user += "Examples:\n" + examples;
        }
        user += "Note: Please do not call functions in DBMS that would bring randomness to the query. Function calls should be deterministic.";
        System.out.println(user);
        try {
            response = getChatGPTResponse(model, system, user);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            response = parseAndGetGPTContent(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return response;
    }

    private String getChatGPTResponse(String model, String system, String user) throws IOException {
        if (apiKey == null) {
            System.err.println("OPENAI_API_KEY environment variable not set");
            return "";
        }
        OkHttpClient client = new OkHttpClient();

        JSONObject json = new JSONObject();

        json.put("model", model);

        // manage the messages
        JSONArray messages = new JSONArray();

        JSONObject message1 = new JSONObject();
        message1.put("role", "system");
        message1.put("content", system);

        JSONObject message2 = new JSONObject();
        message2.put("role", "user");
        message2.put("content", user);
        messages.put(message1);
        messages.put(message2);

        json.put("messages", messages);

        RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json; charset=utf-8"));

        Request request = new Request.Builder().url(chat_url).post(body).addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey).build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    private String parseAndGetGPTContent(String response) {
        JSONObject json = new JSONObject(response);
        JSONArray choices = json.getJSONArray("choices");
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");
        return message.getString("content");
    }

    public String getFragments() {
        return raw_fragments;
    }

    public void setExamples(String examples) {
        this.examples = examples;
    }

    public String getExamples() {
        return examples;
    }
}
