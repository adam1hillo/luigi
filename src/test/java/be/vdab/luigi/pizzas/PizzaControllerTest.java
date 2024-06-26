package be.vdab.luigi.pizzas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@Sql("/pizzas.sql")
@AutoConfigureMockMvc
class PizzaControllerTest {
    private final static Path TEST_RESOURCES = Path.of("src/test/resources");
    private final static String PIZZAS_TABLE = "pizzas";
    private final static String PRIJZEN_TABLE = "prijzen";
    private final MockMvc mockMvc;
    private final JdbcClient jdbcClient;

    PizzaControllerTest(MockMvc mockMvc, JdbcClient jdbcClient) {
        this.mockMvc = mockMvc;
        this.jdbcClient = jdbcClient;
    }

    @Test
    void findAantalVindtHetJuisteAantalPizzas() throws Exception {
        mockMvc.perform(get("/pizzas/aantal"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("$")
                                .value(JdbcTestUtils.countRowsInTable(jdbcClient, PIZZAS_TABLE)));
    }

    private long idVanTest1Pizza()  {
        return jdbcClient.sql("select id from pizzas where naam = 'test1'")
                .query(Long.class)
                .single();
    }
    @Test
    void findByIdMetEenBestaandeIdVindtDePizza() throws Exception {
        long id = idVanTest1Pizza();
        mockMvc.perform(get("/pizzas/{id}", id))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("id").value(id),
                        jsonPath("naam").value("test1"));
    }
    @Test
    void findByIdMetOnbestaandeIdGeeftNotFound() throws Exception {
        mockMvc.perform(get("/pizzas/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }
    @Test
    void findAllVindtAllePizzas() throws Exception {
        mockMvc.perform(get("/pizzas"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("length()")
                                .value(JdbcTestUtils.countRowsInTable(jdbcClient, PIZZAS_TABLE)));
    }
    @Test
    void findByNaamBevatVindtDeJuistePizzas() throws Exception {
        mockMvc.perform(get("/pizzas")
                .param("naamBevat", "test"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("length()").value(
                             JdbcTestUtils.countRowsInTableWhere(jdbcClient, PIZZAS_TABLE, "naam like '%test%'")));
    }
    @Test
    void findByPrijsTussenVindtDeJuistePizzas() throws Exception {
        mockMvc.perform(get("/pizzas")
                .param("vanPrijs", "10")
                .param("totPrijs", "20"))
                .andExpectAll(
                        status().isOk(),
                        jsonPath("length()").value(JdbcTestUtils.countRowsInTableWhere(jdbcClient, PIZZAS_TABLE,
                                "prijs between 10 and 20")));
    }
    @Test
    void deleteVerwijdertDePzza() throws Exception {
        var id = idVanTest1Pizza();
        mockMvc.perform(delete("/pizzas/{id}", id))
                .andExpect(status().isOk());
        assertThat(JdbcTestUtils.countRowsInTableWhere(jdbcClient, PIZZAS_TABLE, "id = " + id)).isZero();
    }
    @Test
    void createVoegtDePizzaToe() throws Exception {
        var jsonData = Files.readString(TEST_RESOURCES.resolve("correctePizza.json"));
        var responseBody = mockMvc.perform(post("/pizzas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonData))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JdbcTestUtils.countRowsInTableWhere(jdbcClient, PIZZAS_TABLE, "naam = 'test3' and id = " + responseBody)).isOne();
        assertThat(JdbcTestUtils.countRowsInTableWhere(jdbcClient, PRIJZEN_TABLE, "prijs = 0.01 and pizzaId = " + responseBody)).isOne();
    }
    @ParameterizedTest
    @ValueSource(strings = {"pizzaZonderNaam.json", "pizzaMetLegeNaam.json", "pizzaZonderPrijs.json", "pizzaMetNegatievePrijs.json"})
    void createMetVerkeerdeDataMislukt(String bestandsNaam) throws Exception {
        var jsonData = Files.readString(TEST_RESOURCES.resolve(bestandsNaam));
        mockMvc.perform(post("/pizzas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonData))
                .andExpect(status().isBadRequest());

    }
    @Test
    void eenPizzaToevoegenDieAlBestaatMislukt() throws Exception {
        var jsonData = Files.readString(TEST_RESOURCES.resolve("pizzaDieAlBestaat.json"));
        mockMvc.perform(post("/pizzas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonData))
                .andExpect(status().isConflict());
    }
    @Test
    void patchWijzigtPrijsEnVoegtPrijsToe() throws Exception {
        long id = idVanTest1Pizza();
        mockMvc.perform(patch("/pizzas/{id}/prijs", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("7.7"))
                .andExpect(status().isOk());
        assertThat(JdbcTestUtils.countRowsInTableWhere(jdbcClient, PIZZAS_TABLE,
                "prijs = 7.7 and id = " + id)).isOne();
        assertThat(JdbcTestUtils.countRowsInTableWhere(jdbcClient, PRIJZEN_TABLE,
                "prijs = 7.7 and pizzaId = " + id)).isOne();
    }
    @Test
    void patchVanOnbestaandePizzaMislukt() throws Exception {
        mockMvc.perform(patch("/pizzas/{id}/prijs", Long.MAX_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("7.7"))
                .andExpect(status().isNotFound());
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "-7"})
    void patchVanVerkeerdePrijsMislukt(String verkeerdePrijs) throws Exception {
        long id = idVanTest1Pizza();
        mockMvc.perform(patch("/pizzas/{id}/prijs", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(verkeerdePrijs))
                .andExpect(status().isBadRequest());
    }

}
