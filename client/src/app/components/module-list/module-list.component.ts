import { Module } from '../../model/module';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ApiService } from 'src/app/service/api.service';

@Component({
  selector: 'app-module-list',
  templateUrl: './module-list.component.html',
  styleUrls: ['./module-list.component.css'],
})
export class ModuleListComponent implements OnInit {
  modules: Module[];

  constructor(public apiService: ApiService, public router: Router) {
    this.modules = [];
  }
  ngOnInit(): void {
    this.apiService.getModules().subscribe((modules: Module[]) => {
      this.modules = modules;
    });
  }
}
