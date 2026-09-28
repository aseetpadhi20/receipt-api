package com.receipts.receipt;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.receipts.ocr.OcrService;

@SpringBootTest
@ActiveProfiles({"local", "test"})
class NullOcrResultTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private OcrService ocr;

    @Test
    void nullOcrTextIsTreatedAsEmptyAndGivesFailed() throws Exception {
        when(ocr.extractText(any(), any(), any())).thenReturn(null);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

        MockMultipartFile file = new MockMultipartFile("file", "blank.png", "image/png", new byte[] {1});
        String body = mvc.perform(multipart("/receipts").file(file))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long receiptId = ((Number) JsonPath.read(body, "$.receipt_id")).longValue();

        mvc.perform(post("/receipts/{id}/process", receiptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemize_status").value("FAILED"))
                .andExpect(jsonPath("$.itemize_note").value("grand total not found"));
    }
}
