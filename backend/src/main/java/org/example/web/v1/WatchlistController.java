package org.example.web.v1;

import jakarta.validation.constraints.NotBlank;
import org.example.application.WatchlistService;
import org.example.web.dto.InstrumentDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Анонимное избранное. Клиент идентифицируется заголовком {@code X-Client-Id}
 * (UUID, генерируется фронтендом и хранится в localStorage).
 */
@RestController
@RequestMapping("/api/v1/watchlist")
public class WatchlistController {

    private static final String CLIENT_HEADER = "X-Client-Id";

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public List<InstrumentDto> list(@RequestHeader(CLIENT_HEADER) String clientId) {
        return watchlistService.list(clientId);
    }

    @PostMapping
    public ResponseEntity<Void> add(@RequestHeader(CLIENT_HEADER) String clientId,
                                    @RequestBody WatchlistRequest request) {
        watchlistService.add(clientId, request.secId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{secId}")
    public ResponseEntity<Void> remove(@RequestHeader(CLIENT_HEADER) String clientId,
                                       @PathVariable String secId) {
        watchlistService.remove(clientId, secId);
        return ResponseEntity.noContent().build();
    }

    public record WatchlistRequest(@NotBlank String secId) {
    }
}
