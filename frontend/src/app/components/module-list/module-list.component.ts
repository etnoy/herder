import { Component, OnInit } from '@angular/core';
import { ApiService } from 'src/app/service/api.service';
import { ModuleList } from 'src/app/model/module-list';

@Component({
  selector: 'app-module-list',
  templateUrl: './module-list.component.html',
})
export class ModuleListComponent implements OnInit {
  moduleList: ModuleList;

  constructor(public apiService: ApiService) {
    this.moduleList = null;
  }

  ngOnInit(): void {
    this.apiService.getModuleList().subscribe((moduleList: ModuleList) => {
      this.moduleList = moduleList;
    });
  }
}
