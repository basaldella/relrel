package org.sssw.relrel;

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Runs the UncommonFacts algorithm on some example pages.
 * 
 * @author Marco Basaldella
 */
public class Starter {
    
    public static void main(String[] args) {
        
        FactFinder ff = new FactFinder();
        ff.setUserAgent("UncommonFacts finder - basaldella.marco@gmail.com");
        
        ff.findUncommonFacts("John Travolta");
        
        
    }
    
}
