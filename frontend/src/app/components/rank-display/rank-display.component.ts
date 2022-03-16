import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-rank',
  templateUrl: './rank-display.component.html',
})
export class RankDisplayComponent {
  @Input()
  rank: number;
}
