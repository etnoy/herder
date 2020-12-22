import { Component, OnInit } from '@angular/core';
import { ApiService } from 'src/app/service/api.service';
import { Score } from 'src/app/model/score';

@Component({
  selector: 'app-scoreboard',
  templateUrl: './scoreboard.component.html',
  styleUrls: ['./scoreboard.component.css'],
})
export class ScoreboardComponent implements OnInit {
  scores: Score[];

  constructor(private apiService: ApiService) {
    this.scores = [];
  }

  ngOnInit(): void {
    this.apiService.getScoreboard().subscribe((scores: Score[]) => {
      this.scores = scores;
    });
  }
}
