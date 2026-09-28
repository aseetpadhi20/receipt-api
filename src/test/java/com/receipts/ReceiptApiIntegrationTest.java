package com.receipts;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class ReceiptApiIntegrationTest {

    private static final Path FIXTURES = Path.of("fixtures", "task-a");

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void cleanReceiptIsComplete() throws Exception {
        process(upload("receipt-clean"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchant").value("Cafe Mitte"))
                .andExpect(jsonPath("$.date").value("2026-03-12"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.grand_total").value(17.85))
                .andExpect(jsonPath("$.taxes[0].name").value("VAT"))
                .andExpect(jsonPath("$.taxes[0].rate").value(0.19))
                .andExpect(jsonPath("$.taxes[0].amount").value(2.85))
                .andExpect(jsonPath("$.taxes[0].inclusive").value(false))
                .andExpect(jsonPath("$.line_items", hasSize(3)))
                .andExpect(jsonPath("$.line_items[1].description").value("Sandwich"))
                .andExpect(jsonPath("$.itemize_status").value("COMPLETE"));
    }

    @Test
    void taxOnlyReceiptNeedsReviewWithNoItems() throws Exception {
        process(upload("receipt-tax-only"))
                .andExpect(jsonPath("$.grand_total").value(24.00))
                .andExpect(jsonPath("$.taxes[0].amount").value(3.83))
                .andExpect(jsonPath("$.taxes[0].inclusive").value(true))
                .andExpect(jsonPath("$.line_items", hasSize(0)))
                .andExpect(jsonPath("$.itemize_status").value("NEEDS_REVIEW"));
    }

    @Test
    void mismatchReceiptNeedsReviewAndKeepsTotal() throws Exception {
        process(upload("receipt-mismatch"))
                .andExpect(jsonPath("$.grand_total").value(18.50))
                .andExpect(jsonPath("$.line_items", hasSize(2)))
                .andExpect(jsonPath("$.itemize_status").value("NEEDS_REVIEW"));
    }

    @Test
    void processingTwiceKeepsTheSameTransaction() throws Exception {
        long receiptId = upload("receipt-clean");
        long firstId = idOf(process(receiptId));

        process(receiptId).andExpect(jsonPath("$.id").value(firstId));
    }

    @Test
    void reitemizeKeepsTheSameTransaction() throws Exception {
        long id = idOf(process(upload("receipt-clean")));

        mvc.perform(post("/transactions/{id}/itemize", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.line_items", hasSize(3)))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void patchThatDoesNotAddUpIs409AndSavesNothing() throws Exception {
        long id = idOf(process(upload("receipt-mismatch")));

        patchItems(id, """
                {"items":[{"description":"Water","amount":4.00},{"description":"Snacks","amount":6.00}]}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ITEMS_DO_NOT_RECONCILE"))
                .andExpect(jsonPath("$.expected_items_total").value(16.60))
                .andExpect(jsonPath("$.actual_items_total").value(10.00));

        mvc.perform(get("/transactions/{id}", id))
                .andExpect(jsonPath("$.line_items[0].source").value("AUTO"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void patchThatAddsUpIsSaved() throws Exception {
        long id = idOf(process(upload("receipt-mismatch")));

        patchItems(id, """
                {"items":[{"description":"Water","amount":4.00},{"description":"Snacks","amount":6.00},
                          {"description":"Minibar","amount":6.60}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemize_status").value("COMPLETE"))
                .andExpect(jsonPath("$.line_items", hasSize(3)))
                .andExpect(jsonPath("$.line_items[2].source").value("USER"));
    }

    @Test
    void patchWithOldVersionIs409() throws Exception {
        long id = idOf(process(upload("receipt-clean")));
        mvc.perform(post("/transactions/{id}/itemize", id));

        patchItems(id, """
                {"version":0,"items":[{"description":"Espresso","amount":15.00}]}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VERSION_CONFLICT"));
    }

    @Test
    void patchWithTooManyDecimalsIs400() throws Exception {
        long id = idOf(process(upload("receipt-clean")));

        patchItems(id, """
                {"items":[{"description":"Espresso","amount":15.001}]}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void patchWithNullItemIs400() throws Exception {
        long id = idOf(process(upload("receipt-clean")));

        patchItems(id, """
                {"items":[null]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("items[0] must not be null"));
    }

    @Test
    void unknownTransactionIs404() throws Exception {
        mvc.perform(get("/transactions/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void unsupportedFileTypeIs415() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/octet-stream", new byte[] {1});

        mvc.perform(multipart("/receipts").file(file))
                .andExpect(status().isUnsupportedMediaType());
    }

    private long upload(String fixture) throws Exception {
        byte[] content = Files.readAllBytes(FIXTURES.resolve(fixture + ".txt"));
        MockMultipartFile file = new MockMultipartFile("file", fixture + ".txt", "text/plain", content);
        String body = mvc.perform(multipart("/receipts").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.receipt_id")).longValue();
    }

    private ResultActions process(long receiptId) throws Exception {
        return mvc.perform(post("/receipts/{id}/process", receiptId));
    }

    private ResultActions patchItems(long transactionId, String json) throws Exception {
        return mvc.perform(patch("/transactions/{id}/items", transactionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static long idOf(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
