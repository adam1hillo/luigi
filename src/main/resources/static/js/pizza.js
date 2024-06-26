"use strict"
import {byId, toon, setText, verberg} from "./util.js";

byId("zoek").onclick = async function () {
    verbergPizzaEnFouten();
    const zoekIdInput = byId("zoekId");
    if (zoekIdInput.checkValidity()) {
        findById(zoekIdInput.value)
    } else {
        toon("zoekIdFout");
        zoekIdInput.focus();
    }
}

byId("bewaar").onclick = async function() {
    const nieuwePrijsInput = byId("nieuwePrijs");
    if (nieuwePrijsInput.checkValidity()) {
        verberg("nieuwePrijsFout");
        updatePrijs(Number(nieuwePrijsInput.value));
    } else {
        toon("nieuwePrijsFout");
        nieuwePrijsInput.focus();
    }
}

function verbergPizzaEnFouten() {
    verberg("pizza");
    verberg("storing")
    verberg("nietGevonden");
    verberg("zoekIdFout");
    verberg("nieuwePrijsFout");
}
async function findById(id) {
    const response = await fetch(`pizzas/${id}`);
    if (response.ok) {
        const pizza = await response.json();
        toon("pizza");
        setText("naam", pizza.naam);
        setText("prijs", pizza.prijs);
    } else {
        if (response.status === 404) {
            toon("nietGevonden");
        } else {
            toon("storing");
        }
    }
}

async function updatePrijs(nieuwePrijs) {
    const response = await fetch(`pizzas/${byId("zoekId").value}/prijs`,
        {
            method: "PATCH",
            headers: {'Content-Type': "application/json"},
            body: JSON.stringify(nieuwePrijs)
        });
    if (response.ok) {
        setText("prijs", nieuwePrijs);
    } else {
        toon("storing");
    }
}
byId("prijzen").onclick = function () {
    const idEnNaam = {
        id: Number(byId("zoekId").value),
        naam: byId("naam").innerText
    };
    sessionStorage.setItem("idEnNaam", JSON.stringify(idEnNaam));
}