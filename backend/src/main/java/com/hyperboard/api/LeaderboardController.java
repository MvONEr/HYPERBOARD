package com.hyperboard.api;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

  private final LeaderboardDao dao;

  public LeaderboardController(LeaderboardDao dao) {
    this.dao = dao;
  }

  @GetMapping
  public List<LeaderboardEntry> leaderboard(
      @RequestParam(defaultValue = "total_pnl") String sort,
      @RequestParam(required = false) String style,
      @RequestParam(defaultValue = "100") int limit) {
    return dao.query(sort, style, limit);
  }
}
