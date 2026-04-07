# 输电通道云图库

一个面向输电通道场景的企业级智能云图库平台，支持公共图库、私有空间、团队空间、批量处理、空间分析和协同编辑。

## 项目亮点

- 支持公共图库、私有空间、团队空间三种资源隔离模式，覆盖个人管理与团队协作场景
- 支持图片上传、URL 导入、批量抓取、批量编辑、颜色检索等高频图库操作
- 支持基于 WebSocket 的多人协同编辑，提升团队处理图片时的实时协作体验
- 提供空间成员权限控制、空间分析看板和管理后台，具备完整的业务闭环
- 集成 PostgreSQL -> MySQL 图像数据同步能力，适合接入外部图像采集系统
- 后端预留分表与扩展能力，便于后续支撑更大规模的图库数据

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Pinia、Vue Router、Ant Design Vue、ECharts
- 后端：Spring Boot、MyBatis-Plus、Redis、Sa-Token、WebSocket、Caffeine
- 数据与存储：MySQL、PostgreSQL、ShardingSphere（预留扩展）、本地文件存储 / COS

## 核心功能

- 用户注册、登录、个人资料维护
- 图片上传、URL 上传、批量采集与批量编辑
- 图片详情展示、颜色搜索、标签分类与审核流程
- 私有空间 / 团队空间创建、成员管理、权限控制
- 空间使用分析、分类分析、标签分析、成员分析、排行分析
- WebSocket 实时协同编辑
- PostgreSQL 图像数据同步到 MySQL

## 目录结构

```text
.
├─ cloud-gallery-frontend   # Vue 3 前端
├─ cloud-gallery-backend    # Spring Boot 后端
└─ integrated_processor.py
```

## 本地启动

### 1. 启动前端

```bash
cd cloud-gallery-frontend
npm install
npm run dev
```

### 2. 启动后端

```bash
cd cloud-gallery-backend
mvn spring-boot:run
```
