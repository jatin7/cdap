/*
 * Copyright © 2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License'); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

var webpack = require('webpack');
var path = require('path');

module.exports = {
  entry: {
    vendor: [
      'react-addons-css-transition-group',
      'whatwg-fetch',
      'node-uuid',
      'sockjs-client',
      'fuse.js',
      'react-dropzone',
      'react-redux',
      'react-router',
      'moment',
      'react-file-download',
      'mousetrap',
      'papaparse',
      'd3',
      'chart.js',
      'cdap-avsc'
    ]
  },
  output: {
    path: path.join(__dirname, 'dll'),
    filename: 'dll.cdap.[name].js',
    library: 'cdap_[name]'
  },
  plugins: [
    new webpack.DefinePlugin({
      'process.env':{
        'NODE_ENV': JSON.stringify("production"),
        '__DEVTOOLS__': false
      },
    }),
    new webpack.DllPlugin({
      path: path.join(__dirname, 'dll', 'cdap-[name]-manifest.json'),
      name: 'cdap_[name]',
      context: path.resolve(__dirname, 'dll')
    }),
    new webpack.optimize.UglifyJsPlugin({
      compress: {
        warnings: false
      }
    })
  ],
  resolve: {
    modules: ['node_modules']
  }
};
