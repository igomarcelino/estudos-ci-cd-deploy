package org.igomarcelino.estudo_ci_cd.controller;

import org.igomarcelino.estudo_ci_cd.dto.MensagemRequestDTO;
import org.igomarcelino.estudo_ci_cd.dto.MensagemResponseDTO;
import org.igomarcelino.estudo_ci_cd.service.MensagemServide;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/mensagens")
public class MensagemController {

    private final MensagemServide mensagemServide;

    public MensagemController(MensagemServide mensagemServide) {
        this.mensagemServide = mensagemServide;
    }

    @PostMapping
    public ResponseEntity<MensagemResponseDTO> save(@RequestBody MensagemRequestDTO dto, UriComponentsBuilder builder){
        var mensagem = mensagemServide.criarMensagem(dto);
        URI uri = builder.path("/mensagens/{id}").buildAndExpand(mensagem.id()).toUri();
        return ResponseEntity.created(uri).body(mensagem);
    }

    @GetMapping
    public ResponseEntity<Page<MensagemResponseDTO>> getAll(Pageable pageable){
        var mensagens =   mensagemServide.mensagens(pageable);
       return ResponseEntity.ok().body(mensagens);
    }
}
