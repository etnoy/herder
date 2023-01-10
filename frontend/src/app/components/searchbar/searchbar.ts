/*
 * Copyright Jonathan Jogenfors, jonathan@jogenfors.se
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import { Component } from '@angular/core';
import { Observable } from 'rxjs';
import {
  debounceTime,
  distinctUntilChanged,
  map,
  filter,
} from 'rxjs/operators';

interface State {
  id: number;
  name: string;
}

const states: State[] = [
  { id: 0, name: 'Alabama' },
  { id: 1, name: 'Alaska' },
  { id: 2, name: 'American Samoa' },
  { id: 3, name: 'Arizona' },
  { id: 4, name: 'Arkansas' },
  { id: 5, name: 'California' },
  { id: 6, name: 'Colorado' },
  { id: 7, name: 'Connecticut' },
  { id: 8, name: 'Delaware' },
  { id: 9, name: 'District Of Columbia' },
  { id: 10, name: 'Federated States Of Micronesia' },
  { id: 11, name: 'Florida' },
  { id: 12, name: 'Georgia' },
  { id: 13, name: 'Guam' },
  { id: 14, name: 'Hawaii' },
  { id: 15, name: 'Idaho' },
  { id: 16, name: 'Illinois' },
  { id: 17, name: 'Indiana' },
  { id: 18, name: 'Iowa' },
  { id: 19, name: 'Kansas' },
  { id: 20, name: 'Kentucky' },
  { id: 21, name: 'Louisiana' },
  { id: 22, name: 'Maine' },
  { id: 23, name: 'Marshall Islands' },
  { id: 24, name: 'Maryland' },
  { id: 25, name: 'Massachusetts' },
  { id: 26, name: 'Michigan' },
  { id: 27, name: 'Minnesota' },
  { id: 28, name: 'Mississippi' },
  { id: 29, name: 'Missouri' },
  { id: 30, name: 'Montana' },
  { id: 31, name: 'Nebraska' },
  { id: 32, name: 'Nevada' },
  { id: 33, name: 'New Hampshire' },
  { id: 34, name: 'New Jersey' },
  { id: 35, name: 'New Mexico' },
  { id: 36, name: 'New York' },
  { id: 37, name: 'North Carolina' },
  { id: 38, name: 'North Dakota' },
  { id: 39, name: 'Northern Mariana Islands' },
  { id: 40, name: 'Ohio' },
  { id: 41, name: 'Oklahoma' },
  { id: 42, name: 'Oregon' },
  { id: 43, name: 'Palau' },
  { id: 44, name: 'Pennsylvania' },
  { id: 45, name: 'Puerto Rico' },
  { id: 46, name: 'Rhode Island' },
  { id: 47, name: 'South Carolina' },
  { id: 48, name: 'South Dakota' },
  { id: 49, name: 'Tennessee' },
  { id: 50, name: 'Texas' },
  { id: 51, name: 'Utah' },
  { id: 52, name: 'Vermont' },
  { id: 53, name: 'Virgin Islands' },
  { id: 54, name: 'Virginia' },
  { id: 55, name: 'Washington' },
  { id: 56, name: 'West Virginia' },
  { id: 57, name: 'Wisconsin' },
  { id: 58, name: 'Wyoming' },
];

@Component({
  selector: 'app-searchbar, app-results',
  templateUrl: './searchbar.html',
  styles: [
    `
      .form-control {
        width: 300px;
      }
    `,
  ],
})
export class SearchbarComponent {
  public model: State;

  formatter = (state: State) => state.name;

  search = (text$: Observable<string>) =>
    text$.pipe(
      debounceTime(200),
      distinctUntilChanged(),
      filter((term) => term.length >= 2),
      map((term) =>
        states
          .filter((state) => new RegExp(term, 'mi').test(state.name))
          .slice(0, 10)
      )
    );
}
