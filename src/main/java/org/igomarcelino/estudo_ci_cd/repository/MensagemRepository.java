package org.igomarcelino.estudo_ci_cd.repository;

import org.igomarcelino.estudo_ci_cd.entity.Mensagem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {
}
