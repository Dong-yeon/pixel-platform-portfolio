package com.pixelfactory.terminal.repository;

import com.pixelfactory.terminal.domain.PopTerminal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PopTerminalRepository extends JpaRepository<PopTerminal, Long> {

    Optional<PopTerminal> findByTerminalCode(String terminalCode);

    List<PopTerminal> findAllByOrderByTerminalCodeAsc();
}
